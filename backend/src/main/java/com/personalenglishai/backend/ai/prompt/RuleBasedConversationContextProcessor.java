package com.personalenglishai.backend.ai.prompt;

import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Message;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Options;
import com.personalenglishai.backend.ai.prompt.ConversationContextProcessor.Result;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component("ruleConversationContextProcessor")
public class RuleBasedConversationContextProcessor implements ConversationContextProcessor {

    @Override
    public Result process(List<Message> rawMessages, Options options) {
        if (rawMessages == null || rawMessages.isEmpty()) {
            return new Result(List.of(), "rule", false, false);
        }

        int recentTurns = options == null ? 8 : Math.max(1, options.recentTurns());
        int start = Math.max(0, rawMessages.size() - recentTurns);
        List<Message> out = new ArrayList<>();
        Message prev = null;
        for (int i = start; i < rawMessages.size(); i++) {
            Message msg = normalize(rawMessages.get(i));
            if (msg == null || isLowSignal(msg.content())) {
                continue;
            }
            if (prev != null && prev.role().equals(msg.role()) && prev.content().equals(msg.content())) {
                continue;
            }
            out.add(msg);
            prev = msg;
        }
        return new Result(List.copyOf(out), "rule", false, false);
    }

    private Message normalize(Message raw) {
        if (raw == null) {
            return null;
        }
        String content = raw.content() == null ? null : raw.content().trim();
        if (content == null || content.isEmpty()) {
            return null;
        }

        String role = raw.role() == null ? "User" : raw.role().trim();
        if (role.isEmpty()) {
            role = "User";
        }
        String lower = role.toLowerCase(Locale.ROOT);
        if (lower.contains("assistant") || lower.contains("ai")) {
            role = "Assistant";
        } else {
            role = "User";
        }
        return new Message(role, content);
    }

    private boolean isLowSignal(String content) {
        if (content == null || content.isBlank()) {
            return true;
        }
        String t = content.trim().toLowerCase(Locale.ROOT);
        if (t.length() <= 2) {
            return true;
        }
        return t.equals("ok")
                || t.equals("okay")
                || t.equals("thanks")
                || t.equals("thank you")
                || t.equals("\u597d\u7684")
                || t.equals("\u8c22\u8c22")
                || t.equals("\u55ef")
                || t.equals("\u6536\u5230");
    }
}
