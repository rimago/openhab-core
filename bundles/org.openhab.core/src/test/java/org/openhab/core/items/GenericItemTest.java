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
package org.openhab.core.items;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.openhab.core.events.EventPublisher;
import org.openhab.core.i18n.UnitProvider;
import org.openhab.core.items.events.ItemEvent;
import org.openhab.core.items.events.ItemStateChangedEvent;
import org.openhab.core.items.events.ItemStateUpdatedEvent;
import org.openhab.core.library.types.OnOffType;
import org.openhab.core.library.types.PercentType;
import org.openhab.core.library.types.RawType;
import org.openhab.core.library.types.StringType;
import org.openhab.core.service.CommandDescriptionService;
import org.openhab.core.service.StateDescriptionService;
import org.openhab.core.types.CommandDescription;
import org.openhab.core.types.CommandOption;
import org.openhab.core.types.State;
import org.openhab.core.types.StateDescriptionFragmentBuilder;
import org.openhab.core.types.StateOption;

/**
 * The GenericItemTest tests functionality of the GenericItem.
 *
 * @author Christoph Knauf - Initial contribution, event tests
 * @author Simon Kaufmann - migrated from Groovy to Java
 */
@NonNullByDefault
@SuppressWarnings("null")
public class GenericItemTest {

    @Test
    public void testItemPostsEventsCorrectly() {
        EventPublisher publisher = mock(EventPublisher.class);

        TestItem item = new TestItem("member1");
        item.setEventPublisher(publisher);
        State oldState = item.getState();

        // State changes -> one update and one change event is fired
        item.setState(new RawType(new byte[0], RawType.DEFAULT_MIME_TYPE));

        ArgumentCaptor<ItemEvent> captor = ArgumentCaptor.forClass(ItemEvent.class);

        verify(publisher, times(2)).post(captor.capture());

        List<ItemEvent> events = captor.getAllValues();
        assertEquals(2, events.size());

        // first event should be updated event
        assertInstanceOf(ItemStateUpdatedEvent.class, events.get(0));
        ItemStateUpdatedEvent updated = (ItemStateUpdatedEvent) events.get(0);
        assertEquals(item.getName(), updated.getItemName());
        assertEquals("openhab/items/member1/stateupdated", updated.getTopic());
        assertEquals(item.getState(), updated.getItemState());
        assertEquals(ItemStateUpdatedEvent.TYPE, updated.getType());

        // second event should be changed event
        assertInstanceOf(ItemStateChangedEvent.class, events.get(1));
        ItemStateChangedEvent change = (ItemStateChangedEvent) events.get(1);
        assertEquals(item.getName(), change.getItemName());
        assertEquals("openhab/items/member1/statechanged", change.getTopic());
        assertEquals(oldState, change.getOldItemState());
        assertEquals(item.getState(), change.getItemState());
        assertEquals(ItemStateChangedEvent.TYPE, change.getType());

        // reset invocations and captor
        clearInvocations(publisher);
        captor = ArgumentCaptor.forClass(ItemEvent.class);

        // State doesn't change -> only update event is fired
        item.setState(item.getState());
        verify(publisher).post(captor.capture());

        events = captor.getAllValues();
        assertEquals(1, events.size()); // two before and one additional

        // event should be updated event
        assertInstanceOf(ItemStateUpdatedEvent.class, events.get(0));
        updated = (ItemStateUpdatedEvent) events.get(0);
        assertEquals(item.getName(), updated.getItemName());
        assertEquals("openhab/items/member1/stateupdated", updated.getTopic());
        assertEquals(item.getState(), updated.getItemState());
        assertEquals(ItemStateUpdatedEvent.TYPE, updated.getType());
    }

    @Test
    public void testAddGroupNameWithNull() {
        TestItem item = new TestItem("member1");
        assertThrows(IllegalArgumentException.class, () -> item.addGroupName(toNull()));
    }

    @Test
    public void testAddGroupNamesWithNull() {
        TestItem item = new TestItem("member1");
        assertThrows(IllegalArgumentException.class,
                () -> item.addGroupNames(Arrays.asList("group-a", toNull(), "group-b")));
    }

    @Test
    public void testRemoveGroupNameWithNull() {
        TestItem item = new TestItem("member1");
        assertThrows(IllegalArgumentException.class, () -> item.removeGroupName(toNull()));
    }

    @Test
    public void testGetStateAsWithSameType() {
        TestItem item = new TestItem("member1");
        item.setState(PercentType.HUNDRED);
        assertEquals(PercentType.class, item.getStateAs(PercentType.class).getClass());
    }

    @Test
    public void testGetStateAsWithDifferentType() {
        TestItem item = new TestItem("member1");
        item.setState(PercentType.HUNDRED);
        assertEquals(OnOffType.class, item.getStateAs(OnOffType.class).getClass());
    }

    @Test
    public void testGetStateAsWithNonConvertible() {
        TestItem item = new TestItem("member1");
        item.setState(StringType.valueOf("Hello World"));
        assertEquals(StringType.class, item.getStateAs(StringType.class).getClass());
    }

    @Test
    public void testGetStateAsWithNull() {
        TestItem item = new TestItem("member1");
        item.setState(StringType.valueOf("Hello World"));
        assertNull(item.getStateAs(toNull()));
    }

    @Test
    public void testDispose() {
        TestItem item = new TestItem("test");
        item.setEventPublisher(mock(EventPublisher.class));
        item.setItemStateConverter(mock(ItemStateConverter.class));
        item.setStateDescriptionService(null);
        item.setUnitProvider(mock(UnitProvider.class));

        item.addStateChangeListener(mock(StateChangeListener.class));

        item.dispose();

        assertNull(item.eventPublisher);
        assertNull(item.itemStateConverter);
        // can not be tested as stateDescriptionProviders is private in GenericItem
        // assertThat(item.stateDescriptionProviders, is(nullValue()));
        assertNull(item.unitProvider);
        assertEquals(0, item.listeners.size());
    }

    @Test
    public void testCommandDescription() {
        TestItem item = new TestItem("test");

        CommandDescriptionService commandDescriptionService = mock(CommandDescriptionService.class);
        when(commandDescriptionService.getCommandDescription("test", null)).thenReturn(new CommandDescription() {

            @Override
            public List<CommandOption> getCommandOptions() {
                return List.of(new CommandOption("ALERT", "Alert"), new CommandOption("REBOOT", "Reboot"));
            }
        });
        item.setCommandDescriptionService(commandDescriptionService);

        assertThat(item.getCommandDescription().getCommandOptions(), hasSize(2));
    }

    @Test
    public void testCommandDescriptionWithLocale() {
        TestItem item = new TestItem("test");

        CommandDescriptionService commandDescriptionService = mock(CommandDescriptionService.class);
        when(commandDescriptionService.getCommandDescription(eq("test"), any(Locale.class)))
                .thenReturn(new CommandDescription() {

                    @Override
                    public List<CommandOption> getCommandOptions() {
                        return List.of(new CommandOption("C1", "Command 1"), new CommandOption("C2", "Command 2"),
                                new CommandOption("C3", "Command 3"));
                    }
                });
        item.setCommandDescriptionService(commandDescriptionService);

        assertThat(item.getCommandDescription(Locale.getDefault()).getCommandOptions(), hasSize(3));
    }

    @Test
    public void commandDescriptionShouldHaveStateOptionsAsCommands() {
        TestItem item = new TestItem("test");

        StateDescriptionService stateDescriptionService = mock(StateDescriptionService.class);
        List<StateOption> stateOptions = List.of(new StateOption("STATE1", "State 1"),
                new StateOption("STATE2", "State 2"));
        when(stateDescriptionService.getStateDescription("test", null)).thenReturn(
                StateDescriptionFragmentBuilder.create().withOptions(stateOptions).build().toStateDescription());
        item.setStateDescriptionService(stateDescriptionService);

        assertThat(item.getCommandDescription().getCommandOptions(), hasSize(2));
    }

    /**
     * Fooling the null-analysis tooling
     *
     * @return always {@code null}
     */
    private <T> T toNull() {
        return null; // :-P
    }
}
