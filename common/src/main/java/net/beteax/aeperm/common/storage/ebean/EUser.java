package net.beteax.aeperm.common.storage.ebean;

import io.ebean.Model;
import io.ebean.annotation.WhenCreated;
import io.ebean.annotation.WhenModified;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ae_user")
public class EUser extends Model {

    @Id
    private UUID uuid;

    @Column(length = 16)
    private String name;

    @Column(name = "primary_group", length = 64)
    private String primaryGroup;

    @Column(name = "groups_json", columnDefinition = "TEXT")
    private String groupsJson = "[]";

    @Column(name = "nodes_json", columnDefinition = "TEXT")
    private String nodesJson = "[]";

    @Column(name = "temp_json", columnDefinition = "TEXT")
    private String tempJson = "[]";

    @WhenCreated
    private Instant createdAt;

    @WhenModified
    private Instant updatedAt;

    public UUID getUuid() {
        return uuid;
    }

    public void setUuid(UUID uuid) {
        this.uuid = uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPrimaryGroup() {
        return primaryGroup;
    }

    public void setPrimaryGroup(String primaryGroup) {
        this.primaryGroup = primaryGroup;
    }

    public String getGroupsJson() {
        return groupsJson;
    }

    public void setGroupsJson(String groupsJson) {
        this.groupsJson = groupsJson;
    }

    public String getNodesJson() {
        return nodesJson;
    }

    public void setNodesJson(String nodesJson) {
        this.nodesJson = nodesJson;
    }

    public String getTempJson() {
        return tempJson;
    }

    public void setTempJson(String tempJson) {
        this.tempJson = tempJson;
    }
}
