/**
 * Waffle (https://github.com/dblock/waffle)
 *
 * Copyright (c) 2010 - 2014 Application Security, Inc.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Application Security, Inc.
 */
package waffle.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author dblock[at]dblock[dot]org
 */
public class NtlmMessageTests {

    @Test
    public void testIsNtlmMessage() {
        assertFalse(NtlmMessage.isNtlmMessage(null));
        final byte[] ntlmSignature = { 0x4e, 0x54, 0x4c, 0x4d, 0x53, 0x53, 0x50, 0x00 };
        assertTrue(NtlmMessage.isNtlmMessage(ntlmSignature));
        final byte[] shortMessage = { 0x4e, 0x54, 0x4c, 0x4d, 0x53, 0x53, 0x50 };
        assertFalse(NtlmMessage.isNtlmMessage(shortMessage));
        final byte[] longMessage = { 0x4e, 0x54, 0x4c, 0x4d, 0x53, 0x53, 0x50, 0x00, 0x00 };
        assertTrue(NtlmMessage.isNtlmMessage(longMessage));
        final byte[] badMessage = { 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
        assertFalse(NtlmMessage.isNtlmMessage(badMessage));
    }

    @Test
    public void testGetNtlmMessageType() {
        final byte[] ntlmMessageType1 = { 0x4e, 0x54, 0x4c, 0x4d, 0x53, 0x53, 0x50, 0x00, 0x01, 0x00, 0x00, 0x00, 0x02,
                0x02, 0x00, 0x00 };
        assertEquals(1, NtlmMessage.getMessageType(ntlmMessageType1));
        final byte[] ntlmMessageType2 = { 0x4e, 0x54, 0x4c, 0x4d, 0x53, 0x53, 0x50, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x02, 0x00, 0x00, 0x01, 0x23, 0x45, 0x67 };
        assertEquals(2, NtlmMessage.getMessageType(ntlmMessageType2));
        final byte[] ntlmMessageType3 = { 0x4e, 0x54, 0x4c, 0x4d, 0x53, 0x53, 0x50, 0x00, 0x03, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x02, 0x02, 0x00, 0x00, 0x01, 0x23, 0x45, 0x67 };
        assertEquals(3, NtlmMessage.getMessageType(ntlmMessageType3));
    }
}
