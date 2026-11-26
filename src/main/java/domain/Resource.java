package domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class Resource {
    private final int id;
    private final String name;
    private final ResourceType type;
}