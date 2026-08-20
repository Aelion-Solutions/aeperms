package sh.aelion.aeperm.common.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ae_user_temp_group")
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserTempGroupEntity {

    @EmbeddedId
    @EqualsAndHashCode.Include
    private UserGroupKey id = new UserGroupKey();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("userUuid")
    @JoinColumn(name = "user_uuid", nullable = false)
    private UserEntity user;

    @Column(name = "expiry", nullable = false)
    private Instant expiry;
}
