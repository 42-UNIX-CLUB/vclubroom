package pt.vclubroom.ops.application;

/** One service on the public status page (E19-S02): "storage", "Images and files", operational. */
public record ServiceStatus(String id, String name, ServiceState status) {
}
