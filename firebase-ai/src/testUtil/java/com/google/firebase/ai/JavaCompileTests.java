/*
 * Copyright 2025 Google LLC
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

import android.graphics.Bitmap;
import androidx.annotation.Nullable;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.ai.FirebaseAI;
import com.google.firebase.ai.GenerativeModel;
import com.google.firebase.ai.ImagenModel;
import com.google.firebase.ai.LiveGenerativeModel;
import com.google.firebase.ai.java.ChatFutures;
import com.google.firebase.ai.java.GenerativeModelFutures;
import com.google.firebase.ai.java.ImagenModelFutures;
import com.google.firebase.ai.java.LiveModelFutures;
import com.google.firebase.ai.java.LiveSessionFutures;
import com.google.firebase.ai.type.BlockReason;
import com.google.firebase.ai.type.Candidate;
import com.google.firebase.ai.type.Citation;
import com.google.firebase.ai.type.CitationMetadata;
import com.google.firebase.ai.type.Content;
import com.google.firebase.ai.type.ContentModality;
import com.google.firebase.ai.type.CountTokensResponse;
import com.google.firebase.ai.type.Dimensions;
import com.google.firebase.ai.type.FileDataPart;
import com.google.firebase.ai.type.FinishReason;
import com.google.firebase.ai.type.FunctionCallPart;
import com.google.firebase.ai.type.FunctionResponsePart;
import com.google.firebase.ai.type.GenerateContentResponse;
import com.google.firebase.ai.type.GenerationConfig;
import com.google.firebase.ai.type.HarmCategory;
import com.google.firebase.ai.type.HarmProbability;
import com.google.firebase.ai.type.HarmSeverity;
import com.google.firebase.ai.type.ImagePart;
import com.google.firebase.ai.type.ImagenBackgroundMask;
import com.google.firebase.ai.type.ImagenEditMode;
import com.google.firebase.ai.type.ImagenEditingConfig;
import com.google.firebase.ai.type.ImagenInlineImage;
import com.google.firebase.ai.type.ImagenMaskReference;
import com.google.firebase.ai.type.InlineDataPart;
import com.google.firebase.ai.type.LiveGenerationConfig;
import com.google.firebase.ai.type.LiveServerContent;
import com.google.firebase.ai.type.LiveServerMessage;
import com.google.firebase.ai.type.LiveServerSetupComplete;
import com.google.firebase.ai.type.LiveServerToolCall;
import com.google.firebase.ai.type.LiveServerToolCallCancellation;
import com.google.firebase.ai.type.MediaData;
import com.google.firebase.ai.type.ModalityTokenCount;
import com.google.firebase.ai.type.Part;
import com.google.firebase.ai.type.PromptFeedback;
import com.google.firebase.ai.type.PublicPreviewAPI;
import com.google.firebase.ai.type.ResponseModality;
import com.google.firebase.ai.type.SafetyRating;
import com.google.firebase.ai.type.Schema;
import com.google.firebase.ai.type.SpeechConfig;
import com.google.firebase.ai.type.TextPart;
import com.google.firebase.ai.type.UsageMetadata;
import com.google.firebase.ai.type.Voice;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import kotlin.OptIn;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonNull;
import kotlinx.serialization.json.JsonObject;
import org.junit.Assert;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

/**
 * Tests in this file exist to be compiled, not invoked
 */
@OptIn(markerClass = PublicPreviewAPI.class)
public class JavaCompileTests {

  public void initializeJava() throws Exception {
    FirebaseAI vertex = FirebaseAI.getInstance();
    GenerativeModel model = vertex.generativeModel("fake-model-name", getConfig());
    LiveGenerativeModel live = vertex.liveModel("fake-model-name", getLiveConfig());
    GenerativeModelFutures futures = GenerativeModelFutures.from(model);
    LiveModelFutures liveFutures = LiveModelFutures.from(live);
    testFutures(futures);
    testLiveFutures(liveFutures);
  }

  private GenerationConfig getConfig() {
    return new GenerationConfig.Builder()
        .setTopK(10)
        .setTopP(11.0F)
        .setTemperature(32.0F)
        .setCandidateCount(1)
        .setMaxOutputTokens(0xCAFEBABE)
        .setFrequencyPenalty(1.0F)
        .setPresencePenalty(2.0F)
        .setStopSequences(List.of("foo", "bar"))
        .setResponseMimeType("image/jxl")
        .setResponseModalities(List.of(ResponseModality.TEXT, ResponseModality.TEXT))
        .setResponseSchema(getSchema())
        .build();
  }

  private Schema getSchema() {
    return Schema.obj(
        Map.of(
            "foo", Schema.numInt(),
            "bar", Schema.numInt("Some integer"),
            "baz", Schema.numInt("Some integer", false),
            "qux", Schema.numDouble(),
            "quux", Schema.numFloat("Some floating point number"),
            "xyzzy", Schema.array(Schema.numInt(), "A list of integers"),
            "fee", Schema.numLong(),
            "ber",
                Schema.obj(
                    Map.of(
                        "bez", Schema.array(Schema.numDouble("Nullable double", true)),
                        "qez", Schema.enumeration(List.of("A", "B", "C"), "One of 3 letters"),
                        "qeez", Schema.str("A funny string")))));
  }

  private LiveGenerationConfig getLiveConfig() {
    return new LiveGenerationConfig.Builder()
        .setTopK(10)
        .setTopP(11.0F)
        .setTemperature(32.0F)
        .setCandidateCount(1)
        .setMaxOutputTokens(0xCAFEBABE)
        .setFrequencyPenalty(1.0F)
        .setPresencePenalty(2.0F)
        .setResponseModality(ResponseModality.AUDIO)
        .setSpeechConfig(new SpeechConfig(new Voice("AOEDE")))
        .build();
  }

  private void testImagen() {
    ImagenModel modelSuspend = FirebaseAI.getInstance().imagenModel("");
    ImagenModelFutures model = ImagenModelFutures.from(modelSuspend);
    model.editImage(
        Collections.singletonList(new ImagenBackgroundMask()),
        "",
        new ImagenEditingConfig(ImagenEditMode.OUTPAINT, 25));
    ImagenMaskReference.generateMaskAndPadForOutpainting(
        new ImagenInlineImage(new byte[0], ""), new Dimensions(0, 0));
  }

  private void testFutures(GenerativeModelFutures futures) throws Exception {
    Content content =
        new Content.Builder()
            .setParts(new ArrayList<>())
            .addText("Fake prompt")
            .addFileData("fakeuri", "image/png")
            .addInlineData(new byte[] {}, "text/json")
            .addImage(Bitmap.createBitmap(0, 0, Bitmap.Config.HARDWARE))
            .addPart(new FunctionCallPart("fakeFunction", Map.of("fakeArg", JsonNull.INSTANCE)))
            .setRole("user")
            .build();
    Executor executor = FirebaseExecutors.directExecutor();
    ListenableFuture<CountTokensResponse> countResponse = futures.countTokens(content);
    validateCountTokensResponse(countResponse.get());
    ListenableFuture<GenerateContentResponse> generateResponse = futures.generateContent(content);
    validateGenerateContentResponse(generateResponse.get());
    ChatFutures chat = futures.startChat();
    ListenableFuture<GenerateContentResponse> future = chat.sendMessage(content);
    future.addListener(
        () -> {
          try {
            validateGenerateContentResponse(future.get());
          } catch (Exception e) {
            // Ignore
          }
        },
        executor);
    Publisher<GenerateContentResponse> responsePublisher = futures.generateContentStream(content);
    responsePublisher.subscribe(
        new Subscriber<GenerateContentResponse>() {
          private boolean complete = false;

          @Override
          public void onSubscribe(Subscription s) {
            s.request(Long.MAX_VALUE);
          }

          @Override
          public void onNext(GenerateContentResponse response) {
            Assert.assertFalse(complete);
            validateGenerateContentResponse(response);
          }

          @Override
          public void onError(Throwable t) {
            // Ignore
          }

          @Override
          public void onComplete() {
            complete = true;
          }
        });
  }

  public void validateCountTokensResponse(CountTokensResponse response) {
    int tokens = response.getTotalTokens();
    Integer billable = response.getTotalBillableCharacters();
    Assert.assertEquals(tokens, response.component1());
    Assert.assertEquals(billable, response.component2());
    Assert.assertEquals(response.getPromptTokensDetails(), response.component3());
    for (ModalityTokenCount count : response.getPromptTokensDetails()) {
      ContentModality modality = count.getModality();
      int tokenCount = count.getTokenCount();
    }
  }

  public void validateGenerateContentResponse(GenerateContentResponse response) {
    List<Candidate> candidates = response.getCandidates();
    if (candidates.size() == 1
        && candidates.get(0).getContent().getParts().stream()
            .anyMatch(p -> p instanceof TextPart && !((TextPart) p).getText().isEmpty())) {
      String text = response.getText();
      Assert.assertNotNull(text);
      Assert.assertFalse(text.isBlank());
    }
    validateCandidates(candidates);
    validateFunctionCalls(response.getFunctionCalls());
    validatePromptFeedback(response.getPromptFeedback());
    validateUsageMetadata(response.getUsageMetadata());
  }

  public void validateCandidates(List<Candidate> candidates) {
    for (Candidate candidate : candidates) {
      validateCitationMetadata(candidate.getCitationMetadata());
      FinishReason reason = candidate.getFinishReason();
      validateSafetyRatings(candidate.getSafetyRatings());
      validateCitationMetadata(candidate.getCitationMetadata());
      validateContent(candidate.getContent());
    }
  }

  public void validateContent(@Nullable Content content) {
    if (content == null) {
      return;
    }
    String role = content.getRole();
    for (Part part : content.getParts()) {
      if (part instanceof TextPart) {
        String text = ((TextPart) part).getText();
      } else if (part instanceof ImagePart) {
        Bitmap bitmap = ((ImagePart) part).getImage();
      } else if (part instanceof InlineDataPart) {
        String mime = ((InlineDataPart) part).getMimeType();
        byte[] data = ((InlineDataPart) part).getInlineData();
      } else if (part instanceof FileDataPart) {
        String mime = ((FileDataPart) part).getMimeType();
        String uri = ((FileDataPart) part).getUri();
      }
    }
  }

  public void validateCitationMetadata(CitationMetadata metadata) {
    if (metadata != null) {
      for (Citation citation : metadata.getCitations()) {
        String uri = citation.getUri();
        String license = citation.getLicense();
        Calendar calendar = citation.getPublicationDate();
        int startIndex = citation.getStartIndex();
        int endIndex = citation.getEndIndex();
        Assert.assertTrue(startIndex <= endIndex);
      }
    }
  }

  public void validateFunctionCalls(List<FunctionCallPart> parts) {
    if (parts != null) {
      for (FunctionCallPart part : parts) {
        String functionName = part.getName();
        Map<String, JsonElement> args = part.getArgs();
        Assert.assertFalse(functionName.isBlank());
      }
    }
  }

  public void validatePromptFeedback(PromptFeedback feedback) {
    if (feedback != null) {
      String message = feedback.getBlockReasonMessage();
      BlockReason reason = feedback.getBlockReason();
      validateSafetyRatings(feedback.getSafetyRatings());
    }
  }

  public void validateSafetyRatings(List<SafetyRating> ratings) {
    for (SafetyRating rating : ratings) {
      Boolean blocked = rating.getBlocked();
      HarmCategory category = rating.getCategory();
      HarmProbability probability = rating.getProbability();
      float score = rating.getProbabilityScore();
      HarmSeverity severity = rating.getSeverity();
      Float severityScore = rating.getSeverityScore();
      if (severity != null) {
        Assert.assertNotNull(severityScore);
      }
    }
  }

  public void validateUsageMetadata(UsageMetadata metadata) {
    if (metadata != null) {
      int totalTokens = metadata.getTotalTokenCount();
      int promptTokenCount = metadata.getPromptTokenCount();
      for (ModalityTokenCount count : metadata.getPromptTokensDetails()) {
        ContentModality modality = count.getModality();
        int tokenCount = count.getTokenCount();
      }
      Integer candidatesTokenCount = metadata.getCandidatesTokenCount();
      for (ModalityTokenCount count : metadata.getCandidatesTokensDetails()) {
        ContentModality modality = count.getModality();
        int tokenCount = count.getTokenCount();
      }
    }
  }

  private void testLiveFutures(LiveModelFutures futures) throws Exception {
    LiveSessionFutures session = futures.connect().get();
    session
        .receive()
        .subscribe(
            new Subscriber<LiveServerMessage>() {
              @Override
              public void onSubscribe(Subscription s) {
                s.request(Long.MAX_VALUE);
              }

              @Override
              public void onNext(LiveServerMessage message) {
                validateLiveContentResponse(message);
              }

              @Override
              public void onError(Throwable t) {
                // Ignore
              }

              @Override
              public void onComplete() {
                // Also ignore
              }
            });

    session.send("Fake message");
    session.send(new Content.Builder().addText("Fake message").build());

    byte[] bytes = new byte[] {(byte) 0xCA, (byte) 0xFE, (byte) 0xBA, (byte) 0xBE};
    session.sendMediaStream(List.of(new MediaData(bytes, "image/jxl")));

    FunctionResponsePart functionResponse =
        new FunctionResponsePart("myFunction", new JsonObject(Map.of()));
    session.sendFunctionResponse(List.of(functionResponse, functionResponse));

    session.startAudioConversation(part -> functionResponse);
    session.startAudioConversation();
    session.stopAudioConversation();
    session.stopReceiving();
    session.close();
  }

  private void validateLiveContentResponse(LiveServerMessage message) {
    if (message instanceof LiveServerContent) {
      LiveServerContent content = (LiveServerContent) message;
      validateContent(content.getContent());
      boolean complete = content.getGenerationComplete();
      boolean interrupted = content.getInterrupted();
      boolean turnComplete = content.getTurnComplete();
    } else if (message instanceof LiveServerSetupComplete) {
      LiveServerSetupComplete setup = (LiveServerSetupComplete) message;
      // No methods
    } else if (message instanceof LiveServerToolCall) {
      LiveServerToolCall call = (LiveServerToolCall) message;
      validateFunctionCalls(call.getFunctionCalls());
    } else if (message instanceof LiveServerToolCallCancellation) {
      LiveServerToolCallCancellation cancel = (LiveServerToolCallCancellation) message;
      List<String> functions = cancel.getFunctionIds();
    }
  }
}
