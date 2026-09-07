# Evidence and results

Every output on this page was produced by running the application locally with
`java -jar target/minimal-http-server.jar` on port `35000`. The screenshots were taken in a real
Chromium browser loading `http://localhost:35000/`.

The last section is reserved for the EC2 run, which has to be captured on the instance launched
with your own AWS learner account.

---

## 1. The page and its resources

![Home page served by the Java server](img/01-home-page.png)

Loading a single page produced five separate HTTP requests. The browser network view shows each
one with its own status and content type:

| Method | Path | Status | Content type | Length |
| --- | --- | --- | --- | --- |
| GET | `/` | 200 | `text/html; charset=UTF-8` | 3581 |
| GET | `/styles.css` | 200 | `text/css; charset=UTF-8` | 2737 |
| GET | `/app.js` | 200 | `text/javascript; charset=UTF-8` | 5305 |
| GET | `/images/logo.png` | 200 | `image/png` | 23196 |
| GET | `/images/request-flow.jpg` | 200 | `image/jpeg` | 15580 |
| GET | `/app/hello?name=Esteban` | 200 | `application/json; charset=UTF-8` | 68 |
| GET | `/app/square?value=abc` | 400 | `application/json; charset=UTF-8` | 61 |
| GET | `/app/time` | 200 | `application/json; charset=UTF-8` | 98 |

The raw capture is stored in [`img/network.json`](img/network.json).

The console of the server shows the same requests arriving one after another in a single process:

```text
Server listening on port 35000 - press Ctrl+C to stop
GET / HTTP/1.1 -> 200 text/html; charset=UTF-8 (3581 bytes)
GET /styles.css HTTP/1.1 -> 200 text/css; charset=UTF-8 (2737 bytes)
GET /app.js HTTP/1.1 -> 200 text/javascript; charset=UTF-8 (5305 bytes)
GET /images/logo.png HTTP/1.1 -> 200 image/png (23196 bytes)
GET /images/request-flow.jpg HTTP/1.1 -> 200 image/jpeg (15580 bytes)
```

---

## 2. Asynchronous requests without reloading the page

A greeting requested from the page: only the result area changed, the page was never reloaded.

![Successful greeting](img/02-greeting-success.png)

The server time comes from the host, not from the browser clock:

![Server time](img/04-server-time.png)

---

## 3. Controlled errors

An invalid value reaches the server, is rejected with `400` and is shown to the user as a readable
message instead of a stack trace:

![Controlled error for invalid input](img/03-invalid-input-error.png)

---

## 4. Protocol evidence

### GET / (home page)

```http
HTTP/1.1 200 OK
Date: Mon, 07 Sep 2026 23:06:04 GMT
Server: minimal-http-server/1.0
Content-Type: text/html; charset=UTF-8
Content-Length: 3581
Connection: close

<!DOCTYPE html>
```

### GET /images/logo.png (binary resource)

```http
HTTP/1.1 200 OK
Date: Mon, 07 Sep 2026 23:06:04 GMT
Server: minimal-http-server/1.0
Content-Type: image/png
Content-Length: 23196
Connection: close
```

### GET /app/hello?name=Ana%20Mar%C3%ADa (dynamic service)

```http
HTTP/1.1 200 OK
Date: Mon, 07 Sep 2026 23:06:04 GMT
Server: minimal-http-server/1.0
Content-Type: application/json; charset=UTF-8
Content-Length: 74
Connection: close

{"service":"greeting","name":"Ana María","greeting":"Hello, Ana María!"}
```

`Content-Length` is 74 for a body of 72 characters: the two accented letters occupy two bytes each,
which is exactly why the length is calculated from the bytes and not from the string.

### GET /app/square?value=abc (invalid input)

```http
HTTP/1.1 400 Bad Request
Content-Type: application/json; charset=UTF-8
Content-Length: 61
Connection: close

{"error":"'abc' is not a number. Use for example 7 or -2.5."}
```

### GET /missing.html (missing resource)

```http
HTTP/1.1 404 Not Found
Content-Type: text/html; charset=UTF-8
Content-Length: 456
Connection: close
```

### POST / (unsupported method)

```http
HTTP/1.1 405 Method Not Allowed
Content-Type: text/html; charset=UTF-8
Content-Length: 494
Allow: GET
Connection: close
```

### GET /../pom.xml (path traversal, sent verbatim with `curl --path-as-is`)

```http
HTTP/1.1 403 Forbidden
Content-Type: text/html; charset=UTF-8
Content-Length: 440
Connection: close
```

The project descriptor was not disclosed. The encoded variant
`/images/%2e%2e/%2e%2e/pom.xml` is refused in the same way, because the path is decoded before it
is normalised.

### A malformed request does not stop the server

```text
$ printf 'THIS IS NOT HTTP\r\n\r\n' | nc localhost 35000
HTTP/1.1 400 Bad Request
Content-Type: text/plain; charset=UTF-8
Content-Length: 54
Connection: close

Bad Request: Unexpected request line: THIS IS NOT HTTP
```

The next request is answered normally: the listening socket stayed open.

---

## 5. Functional test matrix (`scripts/smoke-test.sh`)

```text
Checking http://localhost:35000

Static resources
  ok    home page                    /                                      200 text/html; charset=UTF-8
  ok    style sheet                  /styles.css                            200 text/css; charset=UTF-8
  ok    client script                /app.js                                200 text/javascript; charset=UTF-8
  ok    PNG image                    /images/logo.png                       200 image/png
  ok    JPEG image                   /images/request-flow.jpg               200 image/jpeg

Hardcoded services
  ok    greeting                     /app/hello?name=Esteban                200 application/json; charset=UTF-8
  ok    square                       /app/square?value=7                    200 application/json; charset=UTF-8
  ok    server time                  /app/time                              200 application/json; charset=UTF-8
  ok    health                       /health                                200 application/json; charset=UTF-8

Controlled errors
  ok    greeting without name        /app/hello                             400 application/json; charset=UTF-8
  ok    square with a word           /app/square?value=abc                  400 application/json; charset=UTF-8
  ok    missing static file          /does-not-exist.html                   404 text/html; charset=UTF-8
  ok    unsupported method           /                                      405 text/html; charset=UTF-8
  ok    path traversal               /../pom.xml                            403 text/html; charset=UTF-8
  ok    encoded traversal            /images/%2e%2e/%2e%2e/pom.xml          403 text/html; charset=UTF-8

Repeated requests in one server run
  ok    ten consecutive requests succeeded

All checks passed.
```

Automated suite:

```text
$ mvn test
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- JsonTest
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- ResourcePathsTest
[INFO] Tests run: 6,  Failures: 0, Errors: 0, Skipped: 0 -- ServerConfigTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0 -- WebApplicationTest
[INFO] Tests run: 3,  Failures: 0, Errors: 0, Skipped: 0 -- MediaTypesTest
[INFO] Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0 -- HttpServerIntegrationTest
[INFO] Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0 -- HttpRequestTest
[INFO] Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

---

## 6. The sequential limitation (section 6.2)

Prediction made before running the experiment: *the second user will see the page react
immediately, because the JavaScript client does not block, but the answer will only arrive after
the first request has finished, because the Java server reads one connection at a time.*

Measured with `scripts/observe-sequential-limitation.sh`:

```text
Observing the sequential limitation of http://localhost:35000
  slow request: /app/slow?seconds=6

  slow request   sent at   0.00s   finished at   6.02s   status 200
  fast request   sent at   0.51s   finished at   6.02s   status 200

The fast request was sent half a second after the slow one, but the server could only
start reading it once the slow response had been written: one connection at a time.
```

The fast request needed 5.5 s to be answered although the service it called takes a few
milliseconds. The prediction holds.

**Asynchronous client is not a concurrent server.** `fetch` releases the browser's main thread, so
the second window keeps painting, scrolling and accepting clicks. It does not give the Java process
a second execution path: the accept loop is still one thread that finishes one response before
reading the next connection.

---

## 7. Remote execution on AWS EC2

> Fill this section in with the evidence captured on your own instance, following
> [`docs/aws-deployment.md`](aws-deployment.md). Publish the public DNS name only while the
> instance is alive and never publish keys, account identifiers or credentials.

| Evidence | How to capture it | Status |
| --- | --- | --- |
| `journalctl -u minimal-http-server` showing the service started | on the instance | pending |
| `curl -i http://localhost:35000/health` from inside the instance | on the instance | pending |
| Browser at `http://<public-dns>:35000/` with the page and both images | your computer | pending |
| Browser network view with the three services answering from EC2 | your computer | pending |
| `./scripts/smoke-test.sh http://<public-dns>:35000` output | your computer | pending |
| Security group inbound rules (application port, administration port) | AWS console | pending |
| Instance state `terminated` after the cleanup of section 10 | AWS console | pending |
