package net.runelite.client.plugins.microbot.diagnostics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class BuildProvenanceTest
{
	@Test
	public void officialStableRequiresChannelAndOfficialRepository()
	{
		BuildProvenance provenance = BuildProvenance.resolve("3032e97", "3032e97", false, "stable", "chsami/Microbot");
		assertTrue(provenance.isOfficial());
		assertEquals("official stable (chsami/Microbot workflow)", provenance.getOrigin());
		assertEquals("3032e97", provenance.getCommit());
		assertEquals("build metadata", provenance.getCommitSource());
	}

	@Test
	public void officialNightlyIsDistinguishedFromStable()
	{
		BuildProvenance provenance = BuildProvenance.resolve("7f18208", "7f18208", false, "Nightly", "CHSAMI/microbot");
		assertTrue(provenance.isOfficial());
		assertEquals("official nightly (chsami/Microbot workflow)", provenance.getOrigin());
	}

	@Test
	public void missingChannelIsUnknownNotStable()
	{
		BuildProvenance provenance = BuildProvenance.resolve("nogit", "527c0bf", true, "", "");
		assertFalse(provenance.isOfficial());
		assertTrue(provenance.getOrigin().startsWith("unknown"));
		assertEquals("527c0bf-dirty", provenance.getCommit());
		assertEquals("source tree at build", provenance.getCommitSource());
	}

	@Test
	public void unfilteredPlaceholdersAreUnknown()
	{
		BuildProvenance provenance = BuildProvenance.resolve("${microbot.commit.sha}", "${git.commit.id.abbrev}", null, "${microbot.build.channel}", "${microbot.build.repository}");
		assertFalse(provenance.isOfficial());
		assertEquals("unknown", provenance.getCommit());
		assertTrue(provenance.getOrigin().startsWith("unknown"));
	}

	@Test
	public void forkWorkflowIsCustomEvenWhenStable()
	{
		BuildProvenance provenance = BuildProvenance.resolve("abc1234", null, null, "stable", "someone/Microbot-WalkerV2");
		assertFalse(provenance.isOfficial());
		assertEquals("custom stable build from someone/Microbot-WalkerV2", provenance.getOrigin());
	}

	@Test
	public void channelWithoutRepositoryIsUnverified()
	{
		BuildProvenance provenance = BuildProvenance.resolve("abc1234", null, null, "stable", null);
		assertFalse(provenance.isOfficial());
		assertEquals("unverified stable build (repository unknown)", provenance.getOrigin());
	}

	@Test
	public void unrecognisedOfficialChannelIsUnverified()
	{
		BuildProvenance provenance = BuildProvenance.resolve("abc1234", null, null, "beta", "chsami/Microbot");
		assertFalse(provenance.isOfficial());
		assertEquals("unverified beta build from chsami/Microbot", provenance.getOrigin());
	}
}
