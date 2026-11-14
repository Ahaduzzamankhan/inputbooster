package dev.inputbooster.access;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * Duck interface for the key a key binding is currently bound to.
 *
 * <p>See {@link GuiRenderStateHolder} for why the accessor lives outside the
 * mixin package.
 */
public interface KeyMappingHolder {

    /**
     * The key the binding currently points at.
     *
     * @return the bound key, or {@code null} when the binding has none
     */
    InputConstants.Key inputbooster$boundKey();
}
