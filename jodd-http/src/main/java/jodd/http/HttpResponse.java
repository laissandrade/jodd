// Copyright (c) 2003-present, Jodd Team (http://jodd.org)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright
// notice, this list of conditions and the following disclaimer in the
// documentation and/or other materials provided with the distribution.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package jodd.http;

import jodd.io.StreamUtil;
import jodd.util.StringPool;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.zip.GZIPInputStream;

import static jodd.util.StringPool.CRLF;
import static jodd.util.StringPool.SPACE;

/**
 * HTTP response.
 */
public class HttpResponse extends HttpBase<HttpResponse> {

	protected int statusCode;
	protected String statusPhrase;

	/**
	 * Returns response status code.
	 */
	public int statusCode() {
		return statusCode;
	}

	/**
	 * Sets response status code.
	 */
	public HttpResponse statusCode(int statusCode) {
		this.statusCode = statusCode;
		return this;
	}

	/**
	 * Returns response status phrase.
	 */
	public String statusPhrase() {
		return statusPhrase;
	}

	/**
	 * Sets response status phrase.
	 */
	public HttpResponse statusPhrase(String statusPhrase) {
		this.statusPhrase = statusPhrase;
		return this;
	}

	// ---------------------------------------------------------------- cookie

	/**
	 * Returns list of cookies sent from server.
	 * If no cookie found, returns an empty array.
	 */
	public Cookie[] cookies() {
		List<String> newCookies = headers("set-cookie");

		if (newCookies == null) {
			return new Cookie[0];
		}

		Cookie[] cookies = new Cookie[newCookies.size()];

		for (int i = 0; i < newCookies.size(); i++) {
			String cookieValue = newCookies.get(i);

			Cookie cookie = new Cookie(cookieValue);

			cookies[i] = cookie;
		}

		return cookies;
	}

	// ---------------------------------------------------------------- body

	/**
	 * Unzips GZip-ed body content, removes the content-encoding header
	 * and sets the new content-length value.
	 */
	public HttpResponse unzip() {
		String contentEncoding = contentEncoding();

		if (contentEncoding != null && contentEncoding().equals("gzip")) {
			if (body != null) {
				removeHeader(HEADER_CONTENT_ENCODING);
				try {
					ByteArrayInputStream in = new ByteArrayInputStream(body.getBytes(StringPool.ISO_8859_1));
					GZIPInputStream gzipInputStream = new GZIPInputStream(in);

					ByteArrayOutputStream out = new ByteArrayOutputStream();

					StreamUtil.copy(gzipInputStream, out);

					body(out.toString(StringPool.ISO_8859_1));
				} catch (IOException ioex) {
					throw new HttpException(ioex);
				}
			}
		}
		return this;
	}

	// ---------------------------------------------------------------- buffer


	/**
	 * Creates response {@link jodd.http.Buffer buffer}.
	 */
	@Override
	protected Buffer buffer(boolean fullResponse) {
		// form

		Buffer formBuffer = formBuffer();

		// response

		Buffer response = new Buffer();

		response.append(httpVersion)
			.append(SPACE)
			.append(statusCode)
			.append(SPACE)
			.append(statusPhrase)
			.append(CRLF);

		for (String key : headers.names()) {
			List<String> values = headers.getAll(key);

			String headerName = HttpUtil.prepareHeaderParameterName(key);

			for (String value : values) {
				response.append(headerName);
				response.append(": ");
				response.append(value);
				response.append(CRLF);
			}
		}

		if (fullResponse) {
			response.append(CRLF);

			if (form != null) {
				response.append(formBuffer);
			} else if (body != null) {
				response.append(body);
			}
		}

		return response;
	}

	// ---------------------------------------------------------------- read from

	/**
	 * Reads response input stream and returns {@link HttpResponse response}.
	 * Supports both streamed and chunked response.
	 */
	public static HttpResponse readFrom(InputStream in) {
		InputStreamReader inputStreamReader;
		try {
			inputStreamReader = new InputStreamReader(in, StringPool.ISO_8859_1);
		} catch (UnsupportedEncodingException ignore) {
			return null;
		}
		BufferedReader reader = new BufferedReader(inputStreamReader);

		HttpResponse httpResponse = new HttpResponse();

		// the first line
		String line;
		try {
			line = reader.readLine();
		} catch (IOException ioex) {
			throw new HttpException(ioex);
		}

		if (line != null) {

			line = line.trim();

			int ndx = line.indexOf(' ');
			httpResponse.httpVersion(line.substring(0, ndx));

			int ndx2 = line.indexOf(' ', ndx + 1);
			if (ndx2 == -1) {
				ndx2 = line.length();
			}
			httpResponse.statusCode(Integer.parseInt(line.substring(ndx, ndx2).trim()));

			httpResponse.statusPhrase(line.substring(ndx2).trim());
		}

		httpResponse.readHeaders(reader);
		httpResponse.readBody(reader);

		return httpResponse;
	}

	// ---------------------------------------------------------------- request

	protected HttpRequest httpRequest;

	/**
	 * Binds {@link jodd.http.HttpRequest} to this response.
	 */
	void assignHttpRequest(HttpRequest httpRequest) {
		this.httpRequest = httpRequest;
	}

	/**
	 * Returns {@link jodd.http.HttpRequest} that created this response.
	 */
	public HttpRequest getHttpRequest() {
		return httpRequest;
	}

	/**
	 * Closes requests connection if it was open.
	 * Should be called when using keep-alive connections.
	 * Otherwise, connection will be already closed.
	 */
	public HttpResponse close() {
		HttpConnection httpConnection = httpRequest.httpConnection;
		if (httpConnection != null) {
			httpConnection.close();
			httpRequest.httpConnection = null;
		}
		return this;
	}

}