/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.com.google.firebase.ai;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.TemplateChat;
import com.google.firebase.ai.TemplateGenerativeModel;
import com.google.firebase.ai.java.TemplateChatFutures;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.FunctionCallPart;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerativeBackend;
import com.google.firebase.ai.type.JsonSchema;
import com.google.firebase.ai.type.RequestOptions;
import com.google.firebase.ai.type.TemplateFunctionDeclaration;
import com.google.firebase.ai.type.TemplateTool;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import kotlinx.serialization.json.JsonObject;
import kotlinx.serialization.json.JsonPrimitive;

/**
 * Contains various Java snippets used in documentation for Gemini/Vertex AI.
 * Compilation is a good litmus test that the snippets are sane.
 */
public class JavaSnippetCompileTests {
  // Various stubs for compilation
  private final Executor executor = Executors.newSingleThreadExecutor();

  private Object fetchWeather(String city, String state, String date) {
    return null; // Stub
  }

  // Snippets

  // https://firebase.google.com/docs/ai-logic/server-prompt-templates/multi-turn-interactions?api=dev#chat-use-template-in-code
  public void template_chat() {
    // ...

    // Initialize the Vertex AI Gemini API backend service.
    TemplateGenerativeModel templateModel =
        FirebaseAI.getInstance(GenerativeBackend.vertexAI("global")).templateGenerativeModel();
    // Initialize TemplateChat with history and inputs
    TemplateChat templateChat =
        templateModel.startChat("my-chat-template-v1-0-0", Map.of(), Collections.emptyList());
    TemplateChatFutures chatFutures = TemplateChatFutures.from(templateChat);

    // Send a message asynchronously
    Content prompt = new Content.Builder().addText("I need a copy of my invoice.").build();
    ListenableFuture<GenerateContentResponse> responseFuture = chatFutures.sendMessage(prompt);

    Futures.addCallback(
        responseFuture,
        new FutureCallback<GenerateContentResponse>() {
          @Override
          public void onSuccess(GenerateContentResponse result) {
            System.out.println("Response: " + result.getText());
          }

          @Override
          public void onFailure(Throwable t) {
            t.printStackTrace();
          }
        },
        executor);
  }

  // https://firebase.google.com/docs/ai-logic/server-prompt-templates/multi-turn-interactions?api=dev#function-calling-use-template-in-code
  public void template_functionCallingSchemaInTemplate(String userMessage) {
    // ...

    // Initialize the Vertex AI Gemini API backend service.
    // Create a `TemplateGenerativeModel` instance.
    TemplateGenerativeModel model =
        FirebaseAI.getInstance(GenerativeBackend.vertexAI("global")).templateGenerativeModel();

    // Start a chat session with a template that has functions listed as tools.
    TemplateChat chatSession =
        model.startChat(
            "my-function-calling-template-v1-0-0", // Template ID
            Map.of(), // Inputs
            List.of() // History
            );
    TemplateChatFutures chatFutures = TemplateChatFutures.from(chatSession);

    // Send a message that might trigger a function call.
    Content prompt = new Content.Builder().addText(userMessage).build();
    ListenableFuture<GenerateContentResponse> responseFuture = chatFutures.sendMessage(prompt);

    Futures.addCallback(
        responseFuture,
        new FutureCallback<GenerateContentResponse>() {
          @Override
          public void onSuccess(GenerateContentResponse result) {
            // When the model responds with one or more function calls, invoke the function(s).
            // Note that this is the same as when *not* using server prompt templates.
            for (FunctionCallPart callPart : result.getFunctionCalls()) {
              if ("fetchWeather".equals(callPart.getName())) {
                // Forward the structured input data from the model to the hypothetical external
                // API.
                JsonObject location = (JsonObject) callPart.getArgs().get("location");
                String city = ((JsonPrimitive) location.get("city")).getContent();
                String state = ((JsonPrimitive) location.get("state")).getContent();
                String date = ((JsonPrimitive) callPart.getArgs().get("date")).getContent();
                Object response = fetchWeather(city, state, date);
              }
            }
          }

          @Override
          public void onFailure(Throwable t) {
            t.printStackTrace();
          }
        },
        executor);
  }

  // https://firebase.google.com/docs/ai-logic/server-prompt-templates/multi-turn-interactions?api=dev#function-calling-schema-defined-in-code
  public void template_functionCallingSchemaInCode() {
    TemplateFunctionDeclaration tool =
        new TemplateFunctionDeclaration(
            "fetchWeather", // Name
            JsonSchema.obj( // Parameters
                Map.of(
                    "location",
                        JsonSchema.obj(
                            Map.of(
                                "city", JsonSchema.string("The city of the location."),
                                "state", JsonSchema.string("The state of the location."),
                                "zipCode",
                                    JsonSchema.string(
                                        "Optional zip code of the location", true // Nullable
                                        )),
                            List.of("zipCode"),
                            ""),
                    "date",
                        JsonSchema.string(
                            "The date for which to get the weather. Date must be in the format: YY-MM-DD."),
                    "unit",
                        JsonSchema.enumeration(
                            List.of("CELSIUS", "FAHRENHEIT"),
                            "The temperature unit.",
                            true // Nullable
                            ))),
            JsonSchema.string("A description of the current weather."));
    TemplateGenerativeModel templateModel =
        FirebaseAI.getInstance(GenerativeBackend.vertexAI("global"))
            .templateGenerativeModel(
                new RequestOptions(),
                List.of(TemplateTool.functionDeclarations(List.of(tool), List.of())));
    TemplateChat chat =
        templateModel.startChat(
            "my-function-calling-template-with-no-function-schema-v1-0-0", Map.of(), List.of());
    TemplateChatFutures futures = TemplateChatFutures.from(chat);

    // Send a message that might trigger a function call.
    // ...

    // When the model responds with one or more function calls, invoke the function(s).
    // Note that this is the same as when *not* using server prompt templates.
    // ...

    // Forward the structured input data from the model to the hypothetical external API.
  }
}
