package pt.vclubroom.space.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import pt.vclubroom.space.domain.Channel;

/** findById is inherited; channels are created through the Space aggregate (cascade). */
public interface ChannelRepository extends JpaRepository<Channel, UUID> {
}
