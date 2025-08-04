package dev.overgeneralized.confettinator.client;

import dev.doublekekse.confetti.particle.ConfettiOptions;
import dev.doublekekse.confetti.particle.ConfettiParticle;
import dev.overgeneralized.confettinator.ConfettinatorMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.util.DyeColor;

import java.util.Random;

public class ConfettinatorModClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HandledScreens.register(ConfettinatorMod.CONFETTINATOR_SCREEN_HANDLER, ConfettinatorScreen::new);

        for (int i = 0; i < 16; i++) {
            int decColor = DyeColor.byIndex(i).getEntityColor();
            float r = (float) ((decColor >> 16) & 0xFF) / 255;
            float g = (float) ((decColor >> 8) & 0xFF) / 255;
            float b = (float) (decColor & 0xFF) / 255;
            var currentOptions = new ConfettiOptions.Builder()
                    .setColorSupplier(() -> new float[]{
                            Math.clamp(r + ((new Random().nextFloat() - 0.5F) / 4.0F), 0, 1),
                            Math.clamp(g + ((new Random().nextFloat() - 0.5F) / 4.0F), 0, 1),
                            Math.clamp(b + ((new Random().nextFloat() - 0.5F) / 4.0F), 0, 1)
                    })
                    .build();
            ParticleFactoryRegistry.getInstance().register(ConfettinatorMod.COLOR_CONFETTI_PAR_LIST[i], ConfettiParticle.Provider.customProvider(currentOptions));
        }
    }
}
