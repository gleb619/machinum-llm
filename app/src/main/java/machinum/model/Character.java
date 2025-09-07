package machinum.model;

import lombok.*;

@Data
@AllArgsConstructor
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PUBLIC)
public class Character {

    private String name;
    private String description;
    private String role;
    private String appearance;
    private String personality;

}
