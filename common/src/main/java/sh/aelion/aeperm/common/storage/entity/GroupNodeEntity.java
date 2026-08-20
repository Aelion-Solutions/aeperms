package sh.aelion.aeperm.common.storage.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Setter
@Entity
@Table(name = "ae_group_node")
public class GroupNodeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_name", nullable = false)
    private GroupEntity group;

    @Column(nullable = false, length = 256)
    private String permission;

    @Column(nullable = false)
    private boolean value = true;

    private Instant expiry;

    @ElementCollection
    @CollectionTable(name = "ae_group_node_context", joinColumns = @JoinColumn(name = "node_id"))
    @MapKeyColumn(name = "ctx_key", length = 64)
    @Column(name = "ctx_value", length = 64, nullable = false)
    private Map<String, String> contexts = new LinkedHashMap<>();
}
