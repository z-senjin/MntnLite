package net.runelite.client.plugins.microbot.diagnostics;

import java.util.Locale;
import lombok.Value;

@Value
public class BuildProvenance
{
	public static final String OFFICIAL_REPOSITORY = "chsami/Microbot";

	String commit;
	String commitSource;
	String origin;
	boolean official;

	public static BuildProvenance resolve(String microbotCommit, String sourceCommit, Boolean sourceDirty, String channel, String repository)
	{
		String commit;
		String commitSource;
		if (isKnown(microbotCommit) && !"nogit".equalsIgnoreCase(microbotCommit.trim()))
		{
			commit = microbotCommit.trim();
			commitSource = "build metadata";
		}
		else if (isKnown(sourceCommit))
		{
			commit = sourceCommit.trim() + (Boolean.TRUE.equals(sourceDirty) ? "-dirty" : "");
			commitSource = "source tree at build";
		}
		else
		{
			commit = "unknown";
			commitSource = "not embedded";
		}

		String normalizedChannel = isKnown(channel) ? channel.trim().toLowerCase(Locale.ROOT) : null;
		String normalizedRepository = isKnown(repository) ? repository.trim() : null;
		boolean officialRepository = OFFICIAL_REPOSITORY.equalsIgnoreCase(normalizedRepository);
		boolean knownChannel = "stable".equals(normalizedChannel) || "nightly".equals(normalizedChannel);

		String origin;
		boolean official = false;
		if (normalizedChannel == null)
		{
			origin = "unknown (no build-origin metadata: local, IDE, custom, or older build)";
		}
		else if (normalizedRepository == null)
		{
			origin = "unverified " + normalizedChannel + " build (repository unknown)";
		}
		else if (officialRepository && knownChannel)
		{
			origin = "official " + normalizedChannel + " (" + OFFICIAL_REPOSITORY + " workflow)";
			official = true;
		}
		else if (officialRepository)
		{
			origin = "unverified " + normalizedChannel + " build from " + OFFICIAL_REPOSITORY;
		}
		else
		{
			origin = "custom " + normalizedChannel + " build from " + normalizedRepository;
		}
		return new BuildProvenance(commit, commitSource, origin, official);
	}

	static boolean isKnown(String value)
	{
		return value != null && !value.isBlank() && !value.contains("${");
	}
}
