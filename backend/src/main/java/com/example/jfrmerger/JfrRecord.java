package com.example.jfrmerger;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class JfrRecord {
    private final UUID id;
    private final String fileName;
}
