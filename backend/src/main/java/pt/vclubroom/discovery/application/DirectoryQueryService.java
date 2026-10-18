package pt.vclubroom.discovery.application;

import java.util.List;
import org.springframework.stereotype.Service;

/** Read side of the public landing page and directory. E06-S01-T3 adds landing, search and club profiles. */
@Service
public class DirectoryQueryService {

    private final DiscoveryProperties properties;

    public DirectoryQueryService(DiscoveryProperties properties) {
        this.properties = properties;
    }

    public List<String> categories() {
        return properties.categories();
    }
}
