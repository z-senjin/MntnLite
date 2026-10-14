package net.runelite.client.plugins.microbot.ui;

import net.runelite.client.plugins.config.SearchablePlugin;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import net.runelite.client.plugins.microbot.ui.search.MicrobotPluginSearch;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

final class MicrobotPluginHubOrder
{
    static final String NEWLY_ADDED_FILTER_QUERY = "New";

    private MicrobotPluginHubOrder()
    {
    }

    static boolean isNewlyAddedFilterQuery(String query)
    {
        String trimmed = query == null ? "" : query.trim();
        return NEWLY_ADDED_FILTER_QUERY.equalsIgnoreCase(trimmed)
            || "Newly Added".equalsIgnoreCase(trimmed);
    }

    static <T extends SearchablePlugin> List<T> order(
        Collection<T> plugins,
        String query,
        Function<T, MicrobotPluginManifest> manifest,
        Predicate<T> installed,
        ToIntFunction<T> userCount)
    {
        if (isNewlyAddedFilterQuery(query))
        {
            return plugins.stream()
                .filter(plugin -> manifest.apply(plugin).isNewlyAdded())
                .sorted(newestFirst(manifest))
                .collect(Collectors.toList());
        }

        if (query != null && !query.trim().isEmpty())
        {
            return MicrobotPluginSearch.search(plugins, query);
        }

        return plugins.stream()
            .sorted(popularity(manifest, installed, userCount))
            .collect(Collectors.toList());
    }

    static <T> Comparator<T> newestFirst(Function<T, MicrobotPluginManifest> manifest)
    {
        return Comparator.<T, Instant>comparing(
                plugin -> manifest.apply(plugin).getAddedInstant().orElse(null),
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(
                plugin -> manifest.apply(plugin).getInternalName(),
                Comparator.nullsLast(Comparator.naturalOrder()));
    }

    static <T> Comparator<T> popularity(
        Function<T, MicrobotPluginManifest> manifest,
        Predicate<T> installed,
        ToIntFunction<T> userCount)
    {
        return Comparator.<T, Boolean>comparing(installed::test)
            .thenComparingInt(userCount)
            .reversed()
            .thenComparing(plugin -> manifest.apply(plugin).getInternalName());
    }
}
