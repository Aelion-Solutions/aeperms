package sh.aelion.aeperm.common.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

@Getter
@Setter
@EqualsAndHashCode
@Embeddable
public class UserGroupKey implements Serializable {

    @Column(name = "user_uuid")
    private UUID userUuid;

    @Column(name = "group_name", length = 64)
    private String groupName;
}
