// Copyright 2019 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.testing;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.ml.naturallanguage.FirebaseNaturalLanguage;
import com.google.firebase.ml.naturallanguage.smartreply.FirebaseSmartReply;
import com.google.firebase.ml.naturallanguage.smartreply.FirebaseTextMessage;
import com.google.firebase.ml.naturallanguage.smartreply.SmartReplySuggestion;
import com.google.firebase.ml.naturallanguage.smartreply.SmartReplySuggestionResult;
import com.google.firebase.testing.common.Tasks2;
import java.util.Arrays;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class SmartReplyTest {

    private static final String REMOTE_USER_ID = "some_user_id";
    private static final FirebaseSmartReply smartReply =
            FirebaseNaturalLanguage.getInstance().getSmartReply();

    @Rule public final ActivityTestRule<Activity> activity = new ActivityTestRule<>(Activity.class);

    @Test
    public void suggestReplies_English_Success() throws Exception {
        List<FirebaseTextMessage> messages = Arrays.asList(
                FirebaseTextMessage.createForRemoteUser(
                        "Can I help you?", System.currentTimeMillis(), REMOTE_USER_ID),
                FirebaseTextMessage.createForLocalUser(
                        "No, you can't.", System.currentTimeMillis()),
                FirebaseTextMessage.createForRemoteUser(
                        "Cheers!", System.currentTimeMillis(), REMOTE_USER_ID));
        Task<SmartReplySuggestionResult> task = smartReply.suggestReplies(messages);

        SmartReplySuggestionResult result = Tasks2.waitForSuccess(task);
        List<SmartReplySuggestion> suggestions = result.getSuggestions();

        assertThat(result.getStatus()).isEqualTo(SmartReplySuggestionResult.STATUS_SUCCESS);
        assertThat(suggestions).isNotEmpty();
    }

    @Test
    public void suggestReplies_Espanol_NotSupported() throws Exception {
        List<FirebaseTextMessage> messages = Arrays.asList(
                FirebaseTextMessage.createForLocalUser(
                        "Donede estas?", System.currentTimeMillis()),
                FirebaseTextMessage.createForRemoteUser(
                        "Tengo tres manzanas", System.currentTimeMillis(), REMOTE_USER_ID));
        Task<SmartReplySuggestionResult> task = smartReply.suggestReplies(messages);

        SmartReplySuggestionResult result = Tasks2.waitForSuccess(task);

        assertThat(result.getStatus()).isEqualTo(
                SmartReplySuggestionResult.STATUS_NOT_SUPPORTED_LANGUAGE);
    }

}
