/**
 * Copyright (c) 2010-2023 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.core.semantics;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.ResourceBundle.Control;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.items.Item;
import org.openhab.core.semantics.model.equipment.Equipments;
import org.openhab.core.semantics.model.location.Locations;
import org.openhab.core.semantics.model.point.Measurement;
import org.openhab.core.semantics.model.point.Points;
import org.openhab.core.semantics.model.property.Properties;
import org.openhab.core.types.StateDescription;

/**
 * This is a class that gives static access to the semantic tag library.
 * For everything that is not static, the {@link SemanticsService} should be used instead.
 *
 * @author Kai Kreuzer - Initial contribution
 */
@NonNullByDefault
public class SemanticTags {

    private static final String TAGS_BUNDLE_NAME = "tags";

    private static final Map<String, Class<? extends Tag>> TAGS = new TreeMap<>();

    static {
        Locations.stream().forEach(location -> addTagSet(location));
        Equipments.stream().forEach(equipment -> addTagSet(equipment));
        Points.stream().forEach(point -> addTagSet(point));
        Properties.stream().forEach(property -> addTagSet(property));
    }

    /**
     * Retrieves the class for a given id.
     *
     * @param tagId the id of the tag. The id can be fully qualified (e.g. "Location_Room_Bedroom") or a segment, if
     *            this uniquely identifies the tag
     *            (e.g. "Bedroom").
     * @return the class for the id or null, if non exists.
     */
    public static @Nullable Class<? extends Tag> getById(String tagId) {
        return TAGS.get(tagId);
    }

    public static @Nullable Class<? extends Tag> getByLabel(String tagLabel, Locale locale) {
        Optional<Class<? extends Tag>> tag = TAGS.values().stream().distinct()
                .filter(t -> getLabel(t, locale).equalsIgnoreCase(tagLabel)).findFirst();
        return tag.isPresent() ? tag.get() : null;
    }

    public static List<Class<? extends Tag>> getByLabelOrSynonym(String tagLabelOrSynonym, Locale locale) {
        return TAGS.values().stream().distinct()
                .filter(t -> getLabelAndSynonyms(t, locale).contains(tagLabelOrSynonym.toLowerCase(locale)))
                .collect(Collectors.toList());
    }

    public static List<String> getLabelAndSynonyms(Class<? extends Tag> tag, Locale locale) {
        ResourceBundle rb = ResourceBundle.getBundle(TAGS_BUNDLE_NAME, locale,
                Control.getNoFallbackControl(Control.FORMAT_PROPERTIES));
        try {
            String entry = rb.getString(tag.getAnnotation(TagInfo.class).id());
            return List.of(entry.toLowerCase(locale).split(","));
        } catch (MissingResourceException e) {
            TagInfo tagInfo = tag.getAnnotation(TagInfo.class);
            Stream<String> label = Stream.of(tagInfo.label());
            Stream<String> synonyms = Stream.of(tagInfo.synonyms().split(","));
            return Stream.concat(label, synonyms).map(s -> s.toLowerCase(locale)).distinct().toList();
        }
    }

    public static String getLabel(Class<? extends Tag> tag, Locale locale) {
        ResourceBundle rb = ResourceBundle.getBundle(TAGS_BUNDLE_NAME, locale,
                Control.getNoFallbackControl(Control.FORMAT_PROPERTIES));
        try {
            String entry = rb.getString(tag.getAnnotation(TagInfo.class).id());
            if (entry.contains(",")) {
                return entry.substring(0, entry.indexOf(","));
            } else {
                return entry;
            }
        } catch (MissingResourceException e) {
            return tag.getAnnotation(TagInfo.class).label();
        }
    }

    /**
     * Determines the semantic type of an {@link Item} i.e. a sub-type of {@link Location}, {@link Equipment} or
     * {@link Point}.
     *
     * @param item the Item to get the semantic type for
     * @return a sub-type of Location, Equipment or Point
     */
    public static @Nullable Class<? extends Tag> getSemanticType(Item item) {
        for (String tag : item.getTags()) {
            Class<? extends Tag> type = getById(tag);
            if (type != null && !Property.class.isAssignableFrom(type)) {
                return type;
            }
        }
        // we haven't found any type as a tag, but if there is a Property tag, we can conclude that it is a Point
        if (getProperty(item) != null) {
            StateDescription stateDescription = item.getStateDescription();
            if (stateDescription != null && stateDescription.isReadOnly()) {
                return Measurement.class;
            } else {
                return org.openhab.core.semantics.model.point.Control.class;
            }
        } else {
            return null;
        }
    }

    /**
     * Determines the {@link Property} type that a {@link Point} relates to.
     *
     * @param item the Item to get the property for
     * @return a sub-type of Property if the Item represents a Point, otherwise null
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Class<? extends Property> getProperty(Item item) {
        for (String tag : item.getTags()) {
            Class<? extends Tag> type = getById(tag);
            if (type != null && Property.class.isAssignableFrom(type)) {
                return (Class<? extends Property>) type;
            }
        }
        return null;
    }

    /**
     * Determines the semantic {@link Point} type of an {@link Item}.
     *
     * @param item the Item to get the Point for
     * @return a sub-type of a {@link Point}if the Item represents a Point, otherwise null
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Class<? extends Point> getPoint(Item item) {
        Set<String> tags = item.getTags();
        for (String tag : tags) {
            Class<? extends Tag> type = getById(tag);
            if (type != null && Point.class.isAssignableFrom(type)) {
                return (Class<? extends Point>) type;
            }
        }
        return null;
    }

    /**
     * Determines the semantic {@link Equipment} type of an {@link Item}.
     *
     * @param item the Item to get the Equipment for
     * @return a sub-type of {@link Equipment} if the Item represents an Equipment, otherwise null
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Class<? extends Equipment> getEquipment(Item item) {
        for (String tag : item.getTags()) {
            Class<? extends Tag> type = getById(tag);
            if (type != null && Equipment.class.isAssignableFrom(type)) {
                return (Class<? extends Equipment>) type;
            }
        }
        return null;
    }

    /**
     * Determines the semantic {@link Location} type of an {@link Item}.
     *
     * @param item the item to get the location for
     * @return a sub-type of {@link Location} if the item represents a location, otherwise null
     */
    @SuppressWarnings("unchecked")
    public static @Nullable Class<? extends Location> getLocation(Item item) {
        for (String tag : item.getTags()) {
            Class<? extends Tag> type = getById(tag);
            if (type != null && Location.class.isAssignableFrom(type)) {
                return (Class<? extends Location>) type;
            }
        }
        return null;
    }

    private static void addTagSet(Class<? extends Tag> tagSet) {
        String id = tagSet.getAnnotation(TagInfo.class).id();
        while (id.indexOf("_") != -1) {
            TAGS.put(id, tagSet);
            id = id.substring(id.indexOf("_") + 1);
        }
        TAGS.put(id, tagSet);
    }
}
