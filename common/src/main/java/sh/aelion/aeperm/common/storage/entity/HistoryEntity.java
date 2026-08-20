package sh.aelion.aeperm.common.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "ae_history")
public class HistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "at", nullable = false)
    private Instant at;

    @Column(nullable = false, length = 64)
    private String actor;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false, length = 128)
    private String target;

    @Column(nullable = false, columnDefinition = "text")
    private String detail;
}
