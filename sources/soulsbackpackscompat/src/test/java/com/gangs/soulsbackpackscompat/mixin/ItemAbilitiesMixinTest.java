package com.gangs.soulsbackpackscompat.mixin;

import org.junit.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class ItemAbilitiesMixinTest {
	private static final class TestItem extends ItemAbilitiesMixin {
		private List<?> abilities;
	}

	private static List<?> getAbilities(TestItem item) throws Exception {
		Method hook = ItemAbilitiesMixin.class.getDeclaredMethod("soulsbackpackscompat$repairAbilities",
				CallbackInfoReturnable.class);
		hook.setAccessible(true);
		CallbackInfoReturnable<List<?>> cir = new CallbackInfoReturnable<>("getAbilities", true, item.abilities);
		hook.invoke(item, cir);
		return cir.getReturnValue();
	}

	@Test
	public void missingListIsInitializedAndRemainsMutableAndStable() throws Exception {
		TestItem item = new TestItem();
		assertNull(item.abilities);
		List<?> repaired = getAbilities(item);
		assertNotNull(repaired);
		assertTrue(repaired.isEmpty());
		repaired.add(null);
		assertSame(repaired, getAbilities(item));
		assertEquals(1, getAbilities(item).size());
	}

	@Test
	public void existingWeaponAbilitiesAreRetainedByIdentity() throws Exception {
		TestItem item = new TestItem();
		Object ability = new Object();
		List<Object> existing = new ArrayList<>();
		existing.add(ability);
		item.abilities = existing;
		assertSame(existing, getAbilities(item));
		assertSame(ability, getAbilities(item).get(0));
	}

	@Test
	public void existingEmptyListIsNotReplaced() throws Exception {
		TestItem item = new TestItem();
		List<Object> existing = new ArrayList<>();
		item.abilities = existing;
		assertSame(existing, getAbilities(item));
		assertTrue(getAbilities(item).isEmpty());
	}

	@Test
	public void missingListsAreNotSharedAcrossItems() throws Exception {
		TestItem first = new TestItem();
		TestItem second = new TestItem();
		List<?> firstList = getAbilities(first);
		List<?> secondList = getAbilities(second);
		assertNotSame(firstList, secondList);
		firstList.add(null);
		assertTrue(secondList.isEmpty());
	}

	@Test
	public void validUpstreamListTakesPrecedenceAfterRepair() throws Exception {
		TestItem item = new TestItem();
		List<?> repaired = getAbilities(item);
		List<Object> restored = new ArrayList<>();
		restored.add(new Object());
		item.abilities = restored;
		assertSame(restored, getAbilities(item));
		assertNotSame(repaired, getAbilities(item));
	}
}
