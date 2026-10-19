/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.content;

import java.nio.charset.StandardCharsets;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author eXeLearning contributors
 */
public class ContentTokenTest {

	@Test
	public void testRejectsExpiredForgedAndForeignTokens() {
		String token = ContentToken.issue(_KEY, 1, 2, "1.0", 100);

		Assert.assertNull(ContentToken.verify(_KEY, 1, token, 101));
		Assert.assertNull(ContentToken.verify(_KEY, 9, token, 50));
		Assert.assertNull(
			ContentToken.verify(
				"other".getBytes(StandardCharsets.UTF_8), 1, token, 50));
		Assert.assertNull(
			ContentToken.verify(_KEY, 1, token.replace("2-1.0", "3-1.0"), 50));
		Assert.assertNull(
			ContentToken.verify(_KEY, 1, token.replace("-100-", "-999-"), 50));

		String signature = token.substring(token.lastIndexOf('-') + 1);

		char last = signature.charAt(signature.length() - 1);

		String tampered =
			token.substring(0, token.length() - 1) +
				((last == 'a') ? 'b' : 'a');

		Assert.assertNull(ContentToken.verify(_KEY, 1, tampered, 50));

		Assert.assertNull(ContentToken.verify(_KEY, 1, null, 50));
		Assert.assertNull(ContentToken.verify(_KEY, 1, "garbage", 50));
		Assert.assertNull(
			ContentToken.verify(_KEY, 1, "2-../1.0-100-" + signature, 50));
	}

	@Test
	public void testRoundTrip() {
		String token = ContentToken.issue(_KEY, 1, 2, "1.10", 100);

		Assert.assertTrue(token.matches("2-1\\.10-100-[0-9a-f]{64}"));

		ContentToken contentToken = ContentToken.verify(_KEY, 1, token, 100);

		Assert.assertEquals(2, contentToken.getFileEntryId());
		Assert.assertEquals("1.10", contentToken.getVersion());
		Assert.assertEquals(100, contentToken.getExpiresEpochSeconds());
	}

	@Test(expected = IllegalArgumentException.class)
	public void testSigningWithAnEmptyKeyFails() {
		ContentToken.issue(new byte[0], 1, 2, "1.0", 100);
	}

	private static final byte[] _KEY = "secret-key".getBytes(
		StandardCharsets.UTF_8);

}