/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.content;

import com.liferay.portal.kernel.util.GetterUtil;

import java.nio.charset.StandardCharsets;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Signed, expiring capability that authorizes reading one published revision.
 * Tokens are issued only after a Liferay permission check and travel in the
 * URL path, so relative links inside the sandboxed (opaque origin) content
 * keep working without cookies.
 *
 * <p>
 * Format: <code>{fileEntryId}-{version}-{expiresEpochSeconds}-{hmac}</code>,
 * where the HMAC-SHA256 also covers the company ID.
 * </p>
 *
 * @author eXeLearning contributors
 */
public class ContentToken {

	public static String issue(
		byte[] key, long companyId, long fileEntryId, String version,
		long expiresEpochSeconds) {

		String payload =
			fileEntryId + "-" + version + "-" + expiresEpochSeconds;

		return payload + "-" + _sign(key, companyId, payload);
	}

	/**
	 * Returns the verified token, or <code>null</code> when it is malformed,
	 * forged, issued for another company or expired.
	 */
	public static ContentToken verify(
		byte[] key, long companyId, String token, long nowEpochSeconds) {

		if (token == null) {
			return null;
		}

		Matcher matcher = _tokenPattern.matcher(token);

		if (!matcher.matches()) {
			return null;
		}

		String payload =
			matcher.group(1) + "-" + matcher.group(2) + "-" + matcher.group(3);

		byte[] expected = _sign(
			key, companyId, payload
		).getBytes(
			StandardCharsets.US_ASCII
		);

		if (!MessageDigest.isEqual(
				expected,
				matcher.group(
					4
				).getBytes(
					StandardCharsets.US_ASCII
				))) {

			return null;
		}

		long expires = GetterUtil.getLong(matcher.group(3));

		if (expires < nowEpochSeconds) {
			return null;
		}

		return new ContentToken(
			GetterUtil.getLong(matcher.group(1)), matcher.group(2), expires);
	}

	public long getExpiresEpochSeconds() {
		return _expiresEpochSeconds;
	}

	public long getFileEntryId() {
		return _fileEntryId;
	}

	public String getVersion() {
		return _version;
	}

	private static String _sign(byte[] key, long companyId, String payload) {
		try {
			Mac mac = Mac.getInstance(_ALGORITHM);

			mac.init(new SecretKeySpec(key, _ALGORITHM));

			String message = companyId + ":" + payload;

			byte[] bytes = mac.doFinal(
				message.getBytes(StandardCharsets.UTF_8));

			StringBuilder sb = new StringBuilder(bytes.length * 2);

			for (byte b : bytes) {
				sb.append(Character.forDigit((b >> 4) & 0xF, 16));
				sb.append(Character.forDigit(b & 0xF, 16));
			}

			return sb.toString();
		}
		catch (GeneralSecurityException generalSecurityException) {
			throw new IllegalStateException(generalSecurityException);
		}
	}

	private ContentToken(
		long fileEntryId, String version, long expiresEpochSeconds) {

		_fileEntryId = fileEntryId;
		_version = version;
		_expiresEpochSeconds = expiresEpochSeconds;
	}

	private static final String _ALGORITHM = "HmacSHA256";

	private static final Pattern _tokenPattern = Pattern.compile(
		"(\\d{1,19})-(\\d{1,6}\\.\\d{1,6})-(\\d{1,12})-([0-9a-f]{64})");

	private final long _expiresEpochSeconds;
	private final long _fileEntryId;
	private final String _version;

}