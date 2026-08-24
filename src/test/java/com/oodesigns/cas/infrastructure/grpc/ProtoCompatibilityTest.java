package com.oodesigns.cas.infrastructure.grpc;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtoCompatibilityTest {
    private static final Pattern MESSAGE = Pattern.compile("message\\s+(\\w+)\\s*\\{");
    private static final Pattern FIELD = Pattern.compile("(?:optional\\s+|repeated\\s+)?(?:\\w+|[.\\w]+)\\s+\\w+\\s*=\\s*(\\d+)\\s*;");

    @Test
    void reservesFutureMessageFieldsAndDoesNotReuseNumbers() throws IOException {
        final String source = Files.readString(Path.of("src/main/proto/auth.proto"));
        final List<MessageBody> messages = messageBodies(source);

        for (final MessageBody message : messages) {
            final String name = message.name();
            final String body = message.body();
            assertTrue(body.contains("reserved 1000 to 1999;"),
                () -> name + " must reserve future field numbers");
            assertUniqueFieldNumbers(name, body);
        }

        assertTrue(!messages.isEmpty(), "The protobuf contract must define messages");
    }

    private List<MessageBody> messageBodies(final String source) {
        final List<MessageBody> messages = new ArrayList<>();
        final var messageMatcher = MESSAGE.matcher(source);
        while (messageMatcher.find()) {
            final int openingBrace = messageMatcher.end() - 1;
            int depth = 0;
            for (int position = openingBrace; position < source.length(); position++) {
                final char character = source.charAt(position);
                if (character == '{') {
                    depth++;
                } else if (character == '}' && --depth == 0) {
                    messages.add(new MessageBody(
                        messageMatcher.group(1),
                        source.substring(openingBrace + 1, position)));
                    break;
                }
            }
        }
        return messages;
    }

    private void assertUniqueFieldNumbers(final String messageName, final String body) {
        final var fields = FIELD.matcher(body);
        final Set<Integer> numbers = new HashSet<>();
        while (fields.find()) {
            assertTrue(numbers.add(Integer.valueOf(fields.group(1))),
                () -> messageName + " reuses field number " + fields.group(1));
        }
    }

    private record MessageBody(String name, String body) {
    }
}
