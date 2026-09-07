# Reflection — the eight discussion questions

## 1. Why does a single HTML page cause several HTTP requests?

Because HTML is a description of a document, not the document itself. The response to `GET /` only
contains text and references: `<link rel="stylesheet" href="/styles.css">`,
`<script src="/app.js">`, `<img src="/images/logo.png">`. The browser parses that text, discovers
resources it does not have, and issues one request per reference. Loading the home page of this
application produces five requests before any service is called: the document, the style sheet, the
script and two images — the measured trace is in [`evidence.md`](evidence.md#1-the-page-and-its-resources).

This is why "one user" is a misleading unit of measurement. A hundred students opening the page at
the same time do not create a hundred requests, they create several hundred, and this server
answers them strictly one after another.

## 2. Why must image responses be treated as bytes rather than text?

Because a PNG or a JPEG is not text in any encoding. Reading those files into a `String` would push
them through a character decoder: every byte sequence that is not valid UTF-8 is replaced by the
replacement character, and the file is silently corrupted, usually growing or shrinking in the
process. Writing it back through a `PrintWriter` would corrupt it again.

There is a second reason, which affects text too. `Content-Length` must be the number of **bytes**
of the body, not the number of characters. In `/app/hello?name=Ana María` the body has 72
characters but 74 bytes, because each accented letter takes two bytes in UTF-8. A server that
counted characters would announce a shorter body than the one it sends, and the client would either
truncate the answer or wait for bytes that never arrive.

For those two reasons `HttpResponse` keeps the body as a `byte[]` and computes the length from it,
and `StaticResourceHandler` reads every resource with `readAllBytes()`. Text and images then follow
exactly the same, reliable path.

## 3. What is the role of the response content type?

It is the instruction that tells the browser how to interpret bytes it cannot classify by itself.
The same sequence of bytes is drawn as an image with `image/png`, executed as a program with
`text/javascript`, rendered as a document with `text/html`, or shown as plain characters with
`text/plain`. The file extension on the server is a private convention; the content type is the
public contract.

It also matters for the client code: `response.json()` is only meaningful because the services
announce `application/json`, and the `charset=UTF-8` parameter is what makes `Ana María` display
correctly instead of as mojibake. In this project the association lives in one place, `MediaTypes`,
and a file whose extension is not in that table is not published at all — the server refuses to
serve something it cannot describe correctly.

## 4. What is hardcoded in this design, and what would a routing framework eventually generalize?

Hardcoded today:

- the five service paths, compared with `equals` inside `WebApplication`;
- the binding between a path and the method that answers it;
- the parameter names `name`, `value` and `seconds`, read one by one;
- the validation of each parameter, written again in every service;
- the accepted method, `GET`, checked once at the top;
- the response type of each service, chosen by hand.

A routing framework would generalise exactly those points: a table (or annotations) mapping method
+ path pattern to a handler, path and query parameters extracted and converted automatically,
declarative validation, content negotiation, and a uniform error mapping. The point of writing them
by hand first is that the mechanism becomes visible: a framework does not do anything conceptually
different from these five `if` statements, it only builds the table dynamically and hides the
lookup. Once the mechanism is understood, the framework is a convenience rather than magic.

## 5. Why can the browser remain responsive while the server still handles requests sequentially?

Because they are two different execution contexts with two different limitations.

In the browser, `fetch` does not block the main thread: the request is handed to the network stack,
the JavaScript task ends, and the event loop keeps rendering, scrolling and reacting to clicks. The
answer is delivered later as a promise resolution. That is what makes the page stay interactive and
show a loading state while a request is pending.

On the server there is one thread executing one accept loop. It reads one request, writes one
response, closes the connection and only then accepts the next one. Nothing about the client's
asynchrony reaches it: `fetch` decides when the browser is free, not when the server is free. An
asynchronous client hides latency; it does not create server capacity.

## 6. What changed when the server moved to EC2? What did not change?

**Changed** — the host and the network boundary:

- the address the client uses is a public DNS name instead of `localhost`, so packets now cross the
  Internet, with real latency and a real failure surface;
- reachability became a policy decision: the security group has to allow inbound TCP on the
  application port, and the process has to listen on the wildcard address rather than on loopback;
- the lifecycle became somebody else's job: systemd starts the application, restarts it on failure
  and keeps it running after logout, and the logs moved to the journal;
- the runtime has to be installed and its version documented, and the artifact has to be a single
  self contained file that can be uploaded.

**Did not change** — the architecture:

- the same jar, the same sequential accept loop, the same five hardcoded paths, the same public
  resources;
- one instance is still one capacity limit and one point of failure;
- a slow request still blocks the next one, exactly as it did locally.

The cloud changed *where* the server runs and *who* can reach it. It did not make it scalable.

## 7. What happens when two users send slow requests at almost the same time?

The first connection is accepted and served. The second connection is completed by the operating
system and parked in the accept backlog of the listening socket, which is why the second user does
not see a connection error: the TCP handshake succeeds, so from the outside the request simply
"takes long". The server only reads that second request after it has finished writing the first
response and closed its client socket.

Measured with `scripts/observe-sequential-limitation.sh` — the full output is in
[`evidence.md`](evidence.md#6-the-sequential-limitation-section-62):

```text
slow request   sent at   0.00s   finished at   6.02s   status 200
fast request   sent at   0.51s   finished at   6.02s   status 200
```

The second request asked for a service that takes milliseconds and still needed 5.5 s. The waiting
happens before the server ever looks at it. If more users arrive than the backlog holds, new
connections are refused or dropped, and what was extra latency becomes visible failure.

## 8. What is the next architectural limitation to address, and why should concurrency come before load balancing?

The next limitation is the single request loop: one thread that owns accepting, reading, computing
and writing. The natural next step is to separate accepting from handling — a thread pool, and then
non blocking I/O — so that a slow request occupies one worker instead of the whole server.

Concurrency has to come before load balancing for three reasons:

1. **A load balancer in front of a sequential server multiplies a bad unit.** Ten instances that
   each serve one request at a time cost ten times as much and still stall on the eleventh
   concurrent user. Fixing the unit first is what makes replication worth paying for.
2. **The limit has to be measured before it can be distributed.** Only a server that can overlap
   work reveals where the real bottleneck is — CPU, I/O, a lock, an external dependency.
   Distributing before measuring usually moves the queue instead of removing it.
3. **Distribution imposes requirements that are cheaper to satisfy early.** Several instances
   behind one address only behave identically if the application is stateless, if health checks
   are meaningful, and if configuration comes from the environment. This laboratory already
   satisfies those three conditions — no state is kept between requests, `/health` exists, and the
   port is configurable — which is precisely what makes the *next* step possible.

In short: make one server use its machine well, then add machines. Load balancing is a way to add
capacity, not a way to fix a server that can only do one thing at a time.
