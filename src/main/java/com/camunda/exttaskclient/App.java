package com.camunda.exttaskclient;

import org.camunda.bpm.client.ExternalTaskClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class App {

    private static Random rd = new Random();
    private static Map variables = new HashMap<String, Object>();

    public static void main(String... args) {
        // bootstrap the client
        ExternalTaskClient client = ExternalTaskClient.create()
                .baseUrl("http://localhost:8080/engine-rest")
                .asyncResponseTimeout(1000)
                .build();

        client.subscribe("checkCredit")
                .handler((externalTask, externalTaskService) ->
                    externalTaskService.complete(externalTask, getRandomBooleanVariables("creditAvailable")))
                .open();

        client.subscribe("chargeFromCredit")
                .handler((externalTask, externalTaskService) -> {
                    Double fullAmount = Double.valueOf(externalTask.getVariable("fullAmount").toString());
                    boolean creditSufficient = fullAmount <= 50.0;
                    variables.clear();
                    variables.put("fullyPaid", creditSufficient);
                    variables.put("remainingAmount", creditSufficient ? 0 : fullAmount - 50.0);
                    variables.put("remainingCustomerCredit", creditSufficient ? 50.0 - fullAmount : 0);
                    externalTaskService.complete(externalTask, variables/*getRandomBooleanVariables("fullyPaid")*/);
                })
                .open();

        client.subscribe("chargeFromCreditCard")
                .handler((externalTask, externalTaskService) -> {
                    variables.clear();
                    boolean paymentSuccessful = rd.nextBoolean();
                    boolean insufficientFunds = rd.nextBoolean();
                    variables.put("paymentSuccessful", paymentSuccessful);
                    if (!paymentSuccessful) {

                        Integer retries = externalTask.getRetries() == null ? 2 : externalTask.getRetries();
                        System.out.println("Retrying to charge credit card... " + retries);
                        externalTaskService.handleFailure(externalTask, "Ooops... something went wrong.", "Could not charge credit card.", --retries, 5000);
                    }
                    else if (insufficientFunds) {
                        variables.put("errorResolvable", rd.nextBoolean());
                        variables.put("insufficientFunds", insufficientFunds);
                        externalTaskService.handleBpmnError(externalTask, "creditCardError", "Credit card charge failed.", variables);
                    }
                    else {
                        Object remainingAmountVar = externalTask.getVariable("remainingAmount");
                        Double remainingAmount = Double.valueOf(remainingAmountVar != null ?
                                remainingAmountVar.toString() : externalTask.getVariable("fullAmount").toString());
                        if (paymentSuccessful) {
                            variables.put("chargedFromCreditCard", remainingAmount);
                            variables.put("remainingAmount", 0);
                        }
                        externalTaskService.complete(externalTask, variables);
                    }
                })
                .open();

        client.subscribe("compensateCredit")
                .handler((externalTask, externalTaskService) -> {
                    variables.clear();
                    variables.put("remainingCustomerCredit", 50);
                    externalTaskService.complete(externalTask, variables);
                })
                .open();


 /*       client.subscribe("orderConfirmation").handler((externalTask, externalTaskService) -> {
            Long orderNumber = Math.round(Math.random() * 1000);
            Client wsClient = ClientBuilder.newClient();
            WebTarget webTarget = wsClient.target("http://localhost:8080/engine-rest");
            WebTarget messageTarget = webTarget.path("message");
            Invocation.Builder invocationBuilder = messageTarget.request(MediaType.APPLICATION_JSON);
            String messageBody = "{\"messageName\": \"confirmationMessage\", "
                    + "\"processVariables\": {\"orderNumber\": {\"value\": \""+ orderNumber + "\", \"type\": \"String\"}}}";
            System.out.println(messageBody);
            Response response = invocationBuilder.post(Entity.entity(messageBody , MediaType.APPLICATION_JSON));
            int status = response.getStatus();
            Map<String, Object> variables = new HashMap<>();
            variables.put("statusOfConfirmation", status);
            if (response.getStatus() == 204) {

                System.out.println("Message sent successful:" +  response);
                externalTaskService.complete(externalTask, variables);
            } else {
                System.out.println("Fehler: "+ response);
                externalTaskService.handleFailure(
                        externalTask,
                        "Message failed",
                        response.toString() + ": " + messageBody,
                        0,
                        0);
            }
        }).open();*/
    }

    private static Map getRandomBooleanVariables(String varName) {
        variables.put(varName, rd.nextBoolean());
        return variables;
    }
}
