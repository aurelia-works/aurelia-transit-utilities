package com.aureliatransit.utilities.selftest;

import com.aureliatransit.utilities.client.MtrBridge;
import com.aureliatransit.utilities.client.screen.SpeedAuditScreen;
import com.aureliatransit.utilities.client.screen.TrainInspectorScreen;
import com.aureliatransit.utilities.speed.SpeedAudit;
import net.minecraft.client.MinecraftClient;

import java.util.List;

/** Speed check and train inspector open from the hub and read MTR's live client data. */
final class SpeedAuditSelfTest {

	private SpeedAuditSelfTest() {
	}

	static void addSteps(AtuSelfTest test) {
		Tools.open(test, "Speed check", SpeedAuditScreen.class);
		test.act("check loaded rails", () -> {
			final SpeedAuditScreen screen = (SpeedAuditScreen) MinecraftClient.getInstance().currentScreen;
			final List<SpeedAudit.Rail> rails = MtrBridge.loadedRails();
			test.check("rails within render distance are read", screen.railCountForTest() > 0 && screen.railCountForTest() == rails.size(), screen.railCountForTest() + " rails");
			test.check("every rail has a speed limit in some direction", rails.stream().allMatch(rail -> rail.speed() > 0 || rail.special()), "");
			final long platforms = rails.stream().filter(SpeedAudit.Rail::special).count();
			test.check("platform and siding rails are recognised", platforms > 0, platforms + " special rails");
			screen.findingsForTest().forEach(finding -> AtuSelfTest.LOGGER.info("[ATU-SELFTEST] speed finding: {} {} km/h (expected {}) at {} {} {}", finding.kind(), finding.limit(), finding.expected(), finding.rail().x(), finding.rail().y(), finding.rail().z()));
			test.check("speed check ran", true, screen.findingsForTest().size() + " finding(s)");
		});
		test.screenshot("55_speed");
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
		Tools.open(test, "Train inspector", TrainInspectorScreen.class);
		test.act("check train inspector", () -> {
			final TrainInspectorScreen screen = (TrainInspectorScreen) MinecraftClient.getInstance().currentScreen;
			test.check("train inspector lists the loaded trains", screen.trainsForTest().size() == org.mtr.mod.client.MinecraftClientData.getInstance().vehicles.size(), screen.trainsForTest().size() + " train(s)");
		});
		test.screenshot("56_trains");
		test.act("leave screens", () -> MinecraftClient.getInstance().setScreen(null));
	}
}
