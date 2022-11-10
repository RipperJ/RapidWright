/*
 * Copyright (c) 2022, Xilinx, Inc.
 * Copyright (c) 2022, Advanced Micro Devices, Inc.
 * All rights reserved.
 *
 * Author: Eddie Hung, Xilinx Research Labs.
 *
 * This file is part of RapidWright.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.xilinx.rapidwright.design;

import com.xilinx.rapidwright.device.Device;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.List;

public class TestNet {
    @Test
    void testSetPinsMultiSrc() {
        Design d = new Design("testSetPinsMultiSrc", Device.KCU105);
        SiteInst si = d.createSiteInst("SLICE_X32Y73");

        Net net = new Net("foo");
        List<SitePinInst> pins = Arrays.asList(
                new SitePinInst("A_O", si),
                new SitePinInst("AMUX", si)
        );

        Assertions.assertTrue(net.setPins(pins));
        Assertions.assertEquals(pins.get(0), net.getSource());
        Assertions.assertEquals(pins.get(1), net.getAlternateSource());
    }

    @Test
    void testSetPinsMultiSrcStatic() {
        Design d = new Design("testSetPinsMultiSrcStatic", Device.KCU105);
        SiteInst si = d.createSiteInst("SLICE_X32Y73");

        Net net = d.getVccNet();
        List<SitePinInst> pins = Arrays.asList(
                new SitePinInst("A_O", si),
                new SitePinInst("B_O", si),
                new SitePinInst("C_O", si)
        );

        Assertions.assertTrue(net.setPins(pins));
        Assertions.assertNull(net.getSource());
        Assertions.assertNull(net.getAlternateSource());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testRemovePrimarySourcePinPreserve(boolean preserveOtherRoutes) {
        Design design = new Design("test", Device.KCU105);

        // Net with two outputs (HMUX primary and H_O alternate) and two sinks (SRST_B2 & B2)
        Net net = TestDesignTools.createTestNet(design, "net", new String[]{
                // SLICE_X65Y158/HMUX-> SLICE_X64Y158/SRST_B2
                "INT_X42Y158/INT.LOGIC_OUTS_E16->>INT_NODE_SINGLE_DOUBLE_46_INT_OUT",
                "INT_X42Y158/INT.INT_NODE_SINGLE_DOUBLE_46_INT_OUT->>INT_INT_SINGLE_51_INT_OUT",
                "INT_X42Y158/INT.INT_INT_SINGLE_51_INT_OUT->>INT_NODE_GLOBAL_3_OUT1",
                "INT_X42Y158/INT.INT_NODE_GLOBAL_3_OUT1->>CTRL_W_B7",
                // Adding dual output net
                // SLICE_X65Y158/H_O-> SLICE_X64Y158/B2
                "INT_X42Y158/INT.LOGIC_OUTS_E29->>INT_NODE_QUAD_LONG_5_INT_OUT",
                "INT_X42Y158/INT.INT_NODE_QUAD_LONG_5_INT_OUT->>NN16_BEG3",
                "INT_X42Y174/INT.NN16_END3->>INT_NODE_QUAD_LONG_53_INT_OUT",
                "INT_X42Y174/INT.INT_NODE_QUAD_LONG_53_INT_OUT->>WW4_BEG14",
                "INT_X40Y174/INT.WW4_END14->>INT_NODE_QUAD_LONG_117_INT_OUT",
                "INT_X40Y174/INT.INT_NODE_QUAD_LONG_117_INT_OUT->>SS16_BEG3",
                "INT_X40Y158/INT.SS16_END3->>INT_NODE_QUAD_LONG_84_INT_OUT",
                "INT_X40Y158/INT.INT_NODE_QUAD_LONG_84_INT_OUT->>EE4_BEG12",
                "INT_X42Y158/INT.EE4_END12->>INT_NODE_GLOBAL_8_OUT1",
                "INT_X42Y158/INT.INT_NODE_GLOBAL_8_OUT1->>INT_NODE_IMUX_61_INT_OUT",
                "INT_X42Y158/INT.INT_NODE_IMUX_61_INT_OUT->>IMUX_W0",
        });

        SiteInst si = design.createSiteInst(design.getDevice().getSite("SLICE_X65Y158"));
        SitePinInst src = net.createPin("HMUX", si);
        SitePinInst altSrc = net.createPin("H_O", si);
        Assertions.assertNotNull(net.getAlternateSource());
        Assertions.assertTrue(net.getAlternateSource().equals(altSrc));

        si = design.createSiteInst(design.getDevice().getSite("SLICE_X64Y158"));
        SitePinInst snk = net.createPin("SRST_B2", si);
        snk.setRouted(true);
        SitePinInst altSnk = net.createPin("B2", si);
        altSnk.setRouted(true);

        // Remove the primary source pin
        net.removePin(src, preserveOtherRoutes);
        // Check that alternate source has been promoted to primary
        Assertions.assertEquals(net.getSource(), altSrc);
        Assertions.assertNull(net.getAlternateSource());
        Assertions.assertFalse(snk.isRouted());
        if (preserveOtherRoutes) {
            Assertions.assertEquals(11, net.getPIPs().size());
            Assertions.assertTrue(altSnk.isRouted());
        } else {
            Assertions.assertEquals(0, net.getPIPs().size());
            Assertions.assertFalse(altSnk.isRouted());
        }
    }

    @Test
    public void testRemoveAlternateSourcePin() {
        Design design = new Design("test", Device.KCU105);
        SiteInst si = design.createSiteInst(design.getDevice().getSite("SLICE_X65Y158"));
        Net net = design.createNet("net");
        net.createPin("HMUX", si);
        SitePinInst altSrc = net.createPin("H_O", si);
        Assertions.assertNotNull(net.getAlternateSource());
        Assertions.assertTrue(net.getAlternateSource().equals(altSrc));

        net.removePin(altSrc);
        Assertions.assertNull(net.getAlternateSource());
    }

    @Test
    public void testRemovePinOnStaticNet() {
        Design design = new Design("test", Device.KCU105);
        SiteInst si = design.createSiteInst(design.getDevice().getSite("SLICE_X0Y0"));
        Net gndNet = design.getGndNet();
        SitePinInst a6 = gndNet.createPin("A6", si);
        SitePinInst b6 = gndNet.createPin("B6", si);
        TestDesignTools.addPIPs(gndNet, new String[]{
                "INT_X0Y0/INT.LOGIC_OUTS_E29->>INT_NODE_SINGLE_DOUBLE_101_INT_OUT",
                "INT_X0Y0/INT.INT_NODE_SINGLE_DOUBLE_101_INT_OUT->>SS1_E_BEG7",
                "INT_X0Y0/INT.INT_NODE_IMUX_64_INT_OUT->>IMUX_E16",
                "INT_X0Y0/INT.NN1_E_END0->>INT_NODE_IMUX_64_INT_OUT",
                "INT_X0Y0/INT.INT_NODE_IMUX_64_INT_OUT->>IMUX_E17"
        });
        gndNet.removePin(a6, true);
        Assertions.assertEquals(gndNet.getPIPs().size(), 4);
        gndNet.removePin(b6, true);
        Assertions.assertEquals(gndNet.getPIPs().size(), 0);
    }
}