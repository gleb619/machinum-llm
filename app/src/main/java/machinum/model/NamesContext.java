package machinum.model;

import lombok.*;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@ToString(onlyExplicitlyIncluded = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
@Accessors(chain = true)
public class NamesContext {

    @ToString.Include
    private String id;

    @ToString.Include
    private String chapterId;

    @ToString.Include
    private String name;

    private String category;

    private String description;

    private String translatedName;

    private float[] embedding;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
