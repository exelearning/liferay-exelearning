/**
 * SPDX-FileCopyrightText: (c) 2026 eXeLearning contributors
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package org.exelearning.liferay.elpx;

import org.junit.Assert;
import org.junit.Test;

/**
 * @author eXeLearning contributors
 */
public class ElpxEntryNamesTest {

	@Test
	public void testForbiddenNames() {
		String[] forbidden = {
			".htaccess", "dir/.htpasswd", "WEB.CONFIG", "php.ini", ".user.ini",
			"x.php", "shell.PHP", "a.php.jpg", "a.phtml", "a.phar", "b.shtml",
			"c.jsp", "c.jspx", "c.jspf", "d.asp", "d.aspx", "e.cgi", "f.pl",
			"g.py", "h.php. ", ".htaccess."
		};

		for (String name : forbidden) {
			Assert.assertTrue(name, ElpxEntryNames.isForbidden(name));
		}

		String[] allowed = {
			"index.html", "html/page.html", "libs/jquery.js", "theme/style.css",
			"content/resources/photo.php-diagram.png", "a.python.txt", "README",
			"py", "content.xml"
		};

		for (String name : allowed) {
			Assert.assertFalse(name, ElpxEntryNames.isForbidden(name));
		}
	}

	@Test
	public void testNormalize() throws Exception {
		Assert.assertEquals(
			"index.html", ElpxEntryNames.normalize("index.html"));
		Assert.assertEquals(
			"html/page.html", ElpxEntryNames.normalize("./html//page.html"));
		Assert.assertEquals("html/", ElpxEntryNames.normalize("html/"));
		Assert.assertEquals(
			"content/resources/mi imagen ñ.png",
			ElpxEntryNames.normalize("content/resources/mi imagen ñ.png"));
	}

	@Test
	public void testRejectsUnsafeNames() {
		String[] unsafe = {
			null, "", "/etc/passwd", "../evil.html", "html/../../evil",
			"a/../b", "..", "C:\\windows\\x", "C:/windows/x", "dir\\file",
			"http://example.com/x", "file:x", "a\u0000b", "a\nb", "./", "//",
			_repeat('a', ElpxEntryNames.MAX_NAME_LENGTH + 1),
			"dir/" + _repeat('b', ElpxEntryNames.MAX_SEGMENT_LENGTH + 1)
		};

		for (String name : unsafe) {
			try {
				ElpxEntryNames.normalize(name);

				Assert.fail("Accepted " + name);
			}
			catch (ElpxException elpxException) {
				Assert.assertTrue(
					elpxException.getKey(),
					elpxException.getKey(
					).startsWith(
						"the-package-contains-an-"
					));
			}
		}
	}

	private String _repeat(char c, int count) {
		StringBuilder sb = new StringBuilder();

		for (int i = 0; i < count; i++) {
			sb.append(c);
		}

		return sb.toString();
	}

}