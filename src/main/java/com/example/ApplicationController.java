package com.example;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cloudfoundry.operations.DefaultCloudFoundryOperations;
import org.cloudfoundry.operations.applications.ApplicationDetail;
import org.cloudfoundry.operations.applications.GetApplicationRequest;
import org.cloudfoundry.operations.applications.ScaleApplicationRequest;
import org.cloudfoundry.operations.applications.StopApplicationRequest;
import org.springframework.amqp.rabbit.core.ChannelCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;


import javax.validation.executable.ValidateOnExecution;

/**
 * Created by vince on 11/11/16.
 */

@RestController
public class ApplicationController {
    private int queueThreshold = 1;

    /**
     * Sample variables used to capture information about how the queue is changing over time
     * Not currently implemented in this simple autoscaler
     */
    private int consumerThreshold = 1;
    private int consecutiveQueueThresholdSamples = 0;
    private int consecutiveConsumerThresholdSamples = 0;
    private int windowSize = 1;

    @Value("${cf.scale}")
    private int SCALEFACTOR;
    @Value("${cf.scalemin}")
    private int SCALEDOWN;

    private ApplicationDetail application;
    private int currentAIs;

    @Value("${cf.application}")
    private String appName;

    @Value("${rabbit.queue}")
    private String queueName;

    Log log = LogFactory.getLog(this.getClass());

    @Autowired
    DefaultCloudFoundryOperations cfOps;
    @Autowired
    RabbitTemplate rmq;

    @RequestMapping("/scaleup")
    @ResponseStatus(HttpStatus.OK)
    public void scaleupApp() {

        application = cfOps.applications().get(GetApplicationRequest.builder().name(appName).build()).block();
        currentAIs = application.getInstances();
        int queueSize = getQueueCount(queueName);

        log.info("App Name : " + appName);
        log.info("# of instances : " + currentAIs);
        log.info("Queue name : " + queueName);
        log.info("Queue size : " + getQueueCount(queueName));

        Mono<Void> complete;

        if (needToScale(queueSize)) {
            sampleQueueSize(queueSize);
            //sampleConsumers(stats.consumers);

            complete = scaleUp();
            complete.block();

            log.info(appName + " has been scaled to " + (currentAIs += SCALEFACTOR) + " instances.");
        }
    }

    @RequestMapping("/scaledown")
    @ResponseStatus(HttpStatus.OK)
    public void scaledownApp() {

        application = cfOps.applications().get(GetApplicationRequest.builder().name(appName).build()).block();
        currentAIs = application.getInstances();

        log.info("App Name : " + appName);
        log.info("# of instances : " + currentAIs);

        Mono<Void> complete = scaleDown();
        complete.block();

        log.info(appName + " has been scaled to " + (SCALEDOWN) + " instances.");

    }

    @RequestMapping("/stop")
    @ResponseStatus(HttpStatus.OK)
    public void stopApp() {

        application = cfOps.applications().get(GetApplicationRequest.builder().name(appName).build()).block();
        currentAIs = application.getInstances();

        log.info("App Name : " + appName);
        log.info("# of instances : " + currentAIs);

        Mono<Void> complete = stop();
        complete.block();

        log.info(appName + " has been stopped.");

    }

    @RequestMapping("/queueThreshold")
    @ResponseStatus(HttpStatus.OK)
    public void updateThreshold(@RequestParam("num") int num) {
        if(num != queueThreshold) {
            queueThreshold = num;
        }
    }

    /**
     * TODO - Add algorithm
     * Based off the current queue size sample. This could be reactive however currently a spotcheck durring
     * a long batch process
     */
    private boolean needToScale(int msgs) {

        if(msgs > queueThreshold)
            return true;

        else
            return false;
    }

    private void sampleQueueSize(int msgs) {
        if (msgs > this.queueThreshold) {
            this.consecutiveQueueThresholdSamples += 1;
        } else {
            this.consecutiveQueueThresholdSamples = 0;
        }
    }

    private void sampleConsumers(int consumers) {
        if (consumers > this.consumerThreshold) {
            this.consecutiveConsumerThresholdSamples += 1;
        } else {
            this.consecutiveConsumerThresholdSamples = 0;
        }
    }

    private Mono<Void> scaleUp() {
        return cfOps.applications().scale(ScaleApplicationRequest.builder().name(appName).instances(currentAIs + SCALEFACTOR).build());
    }

    private Mono<Void>  scale(int numInstance) {
        return cfOps.applications().scale(ScaleApplicationRequest.builder().name(appName).instances(currentAIs + numInstance).build());
    }

    private Mono<Void>  scaleDown() {
        return cfOps.applications().scale(ScaleApplicationRequest.builder().name(appName).instances(SCALEDOWN).build());
    }

    private Mono<Void>  stop() {
        return cfOps.applications().stop(StopApplicationRequest.builder().name(appName).build());
    }

    protected int getQueueCount(final String name) {
        AMQP.Queue.DeclareOk declareOk = rmq.execute(new ChannelCallback<AMQP.Queue.DeclareOk>() {
            public AMQP.Queue.DeclareOk doInRabbit(Channel channel) throws Exception {
                return channel.queueDeclarePassive(name);
            }
        });
        return declareOk.getMessageCount();
    }

}