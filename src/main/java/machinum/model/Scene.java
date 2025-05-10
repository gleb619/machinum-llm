package machinum.model;

import lombok.*;

import java.util.List;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Scene {

    private Integer sceneNumber;
    private String location;
    private List<String> characters;
    private List<String> keyEvents;
    private String tone;

}
