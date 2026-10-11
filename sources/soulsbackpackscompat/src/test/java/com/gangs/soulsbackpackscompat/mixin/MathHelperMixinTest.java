package com.gangs.soulsbackpackscompat.mixin;

import org.junit.Test;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;

public class MathHelperMixinTest {
	private static int guardedMultiply(int itemMaxCount, int multiplier) throws Exception {
		Method hook = MathHelperMixin.class.getDeclaredMethod(
				"soulsbackpackscompat$guardZeroStackCount", int.class, int.class,
				CallbackInfoReturnable.class);
		hook.setAccessible(true);
		CallbackInfoReturnable<Integer> cir = new CallbackInfoReturnable<>("intMaxCappedMultiply", true);
		hook.invoke(null, itemMaxCount, multiplier, cir);
		return cir.getReturnValue() == null ? itemMaxCount * multiplier : cir.getReturnValue();
	}

	@Test
	public void zeroStackCountReturnsZeroWithoutDivision() throws Exception {
		assertEquals(0, guardedMultiply(0, 8));
	}

	@Test
	public void negativeStackCountReturnsZeroWithoutDivision() throws Exception {
		assertEquals(0, guardedMultiply(-1, 8));
	}

	@Test
	public void validStackCountIsLeftToUpstreamArithmetic() throws Exception {
		assertEquals(16, guardedMultiply(2, 8));
	}
}
