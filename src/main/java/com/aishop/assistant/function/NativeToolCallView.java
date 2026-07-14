package com.aishop.assistant.function;

public record NativeToolCallView(
        String id,
        String name,
        String arguments
) {
}
