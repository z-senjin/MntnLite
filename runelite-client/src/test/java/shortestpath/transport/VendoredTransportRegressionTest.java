package shortestpath.transport;

import java.util.Collections;
import net.runelite.api.Skill;
import org.junit.Test;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;
import shortestpath.leagues.LeagueRegion;
import shortestpath.transport.parser.SkillRequirementParser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class VendoredTransportRegressionTest
{
	@Test
	public void emptyLeagueUnlockSetProducesAnEmptySnapshot()
	{
		LeagueModeState state = new LeagueModeState();

		state.setForTest(true, Collections.emptySet());

		assertFalse(state.isUnlocked(LeagueRegion.ASGARNIA));
	}

	@Test
	public void multiWordSpecialSkillRequirementsArePreserved()
	{
		int[] levels = new SkillRequirementParser().parse(
			"70 Total level;85 Combat level;120 Quest points");
		int specialOffset = Skill.values().length;

		assertEquals(70, levels[specialOffset]);
		assertEquals(85, levels[specialOffset + 1]);
		assertEquals(120, levels[specialOffset + 2]);
	}

	@Test
	public void mergedTransportKeepsDestinationRegionOverride()
	{
		Transport origin = new Transport.TransportBuilder()
			.origin(WorldPointUtil.packWorldPoint(3200, 3200, 0))
			.destination(Transport.UNDEFINED_DESTINATION)
			.type(TransportType.TRANSPORT)
			.regionOverride(LeagueRegion.ASGARNIA)
			.build();
		Transport destination = new Transport.TransportBuilder()
			.origin(Transport.UNDEFINED_ORIGIN)
			.destination(WorldPointUtil.packWorldPoint(3201, 3200, 0))
			.type(TransportType.TRANSPORT)
			.regionOverride(LeagueRegion.DESERT)
			.build();

		Transport merged = new Transport(origin, destination);

		assertSame(LeagueRegion.DESERT, merged.getRegionOverride());
	}
}
