package net.beteax.aeperm.common.storage.ebean;

import io.ebean.Model;
import io.ebean.annotation.WhenCreated;
import io.ebean.annotation.WhenModified;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "ae_group")
public class EGroup extends Model {

    @Id
    @Column(length = 64)
    private String name;

    private int weight;

    @Column(name = "parents_json", columnDefinition = "TEXT")
    private String parentsJson = "[]";

    @Column(name = "nodes_json", columnDefinition = "TEXT")
    private String nodesJson = "[]";

    @WhenCreated
    private Instant createdAt;

    @WhenModified
    private Instant updatedAt;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getWeight() {
        return weight;
    }

    public void setWeight(int weight) {
        this.weight = weight;
    }

    public String getParentsJson() {
        return parentsJson;
    }

    public void setParentsJson(String parentsJson) {
        this.parentsJson = parentsJson;
    }

    public String getNodesJson() {
        return nodesJson;
    }

    public void setNodesJson(String nodesJson) {
        this.nodesJson = nodesJson;
    }
}
