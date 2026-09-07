/*
 * Asynchronous client of the mini web application.
 *
 * The page must never reload when a service is called, so every action prevents the default form
 * submission, builds the service URL from validated input, performs the request with fetch and
 * updates only the result area. A failed HTTP response and a network failure are reported
 * differently, because they are different problems: the first one is an answer from the server,
 * the second one means no answer arrived at all.
 */
(() => {
  'use strict';

  const statusBox = document.getElementById('status');
  const resultBox = document.getElementById('result');
  const resultHeadline = document.getElementById('result-headline');
  const resultJson = document.getElementById('result-json');
  const errorBox = document.getElementById('error');
  const actions = Array.from(document.querySelectorAll('button'));

  /** Shows the loading state without blocking the interface. */
  function showLoading(label) {
    statusBox.textContent = 'Requesting ' + label + '…';
    statusBox.classList.add('loading');
    resultBox.hidden = true;
    errorBox.hidden = true;
    actions.forEach((button) => { button.disabled = true; });
  }

  function stopLoading() {
    statusBox.classList.remove('loading');
    actions.forEach((button) => { button.disabled = false; });
  }

  function showResult(headline, payload, elapsedMillis) {
    statusBox.textContent = 'Answered in ' + elapsedMillis + ' ms.';
    resultHeadline.textContent = headline;
    resultJson.textContent = JSON.stringify(payload, null, 2);
    resultBox.hidden = false;
    errorBox.hidden = true;
  }

  function showError(title, detail) {
    statusBox.textContent = 'The last request did not succeed.';
    errorBox.textContent = title + (detail ? ' — ' + detail : '');
    errorBox.hidden = false;
    resultBox.hidden = true;
  }

  /**
   * Calls one service and updates the page.
   *
   * @param {string} url        service URL already built from validated input
   * @param {string} label      text shown while the request is pending
   * @param {function} describe turns the parsed JSON into the sentence shown to the user
   */
  async function callService(url, label, describe) {
    showLoading(label);
    const startedAt = performance.now();

    let response;
    try {
      response = await fetch(url, { headers: { Accept: 'application/json' } });
    } catch (networkFailure) {
      // No HTTP status exists here: the request never completed.
      stopLoading();
      showError('The server could not be reached',
        'The connection failed before any response arrived. Check that the server is running '
        + 'and that the port is reachable.');
      return;
    }

    let payload;
    try {
      payload = await response.json();
    } catch (invalidBody) {
      stopLoading();
      showError('HTTP ' + response.status + ': the response was not valid JSON',
        'The server answered with ' + (response.headers.get('Content-Type') || 'an unknown type') + '.');
      return;
    }

    stopLoading();
    const elapsed = Math.round(performance.now() - startedAt);

    // The status is inspected before the body is interpreted as a successful answer.
    if (!response.ok) {
      showError('HTTP ' + response.status + ' ' + response.statusText,
        payload && payload.error ? payload.error : 'The request was rejected by the server.');
      return;
    }

    showResult(describe(payload), payload, elapsed);
  }

  document.getElementById('greeting-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const name = document.getElementById('name').value.trim();
    if (name === '') {
      showError('Enter a name before asking for a greeting', 'The field cannot be empty.');
      return;
    }
    callService('/app/hello?name=' + encodeURIComponent(name), 'a greeting',
      (payload) => payload.greeting);
  });

  document.getElementById('square-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const raw = document.getElementById('value').value.trim();
    if (raw === '' || Number.isNaN(Number(raw))) {
      showError('Enter a valid number before asking for its square',
        '"' + raw + '" is not a number.');
      return;
    }
    callService('/app/square?value=' + encodeURIComponent(raw), 'the square',
      (payload) => 'The square of ' + payload.input + ' is ' + payload.square + '.');
  });

  document.getElementById('time-button').addEventListener('click', () => {
    callService('/app/time', 'the server time',
      (payload) => 'The server clock reads ' + payload.serverTime + ' (' + payload.zone + ').');
  });

  document.getElementById('health-button').addEventListener('click', () => {
    callService('/health', 'the health service',
      (payload) => 'The server is ' + payload.status + ' and has been up for '
        + payload.uptimeSeconds + ' s.');
  });

  document.getElementById('slow-button').addEventListener('click', () => {
    const seconds = document.getElementById('seconds').value.trim();
    callService('/app/slow?seconds=' + encodeURIComponent(seconds || '5'),
      'a deliberately slow answer',
      (payload) => 'The server was busy for ' + payload.actualMillis + ' ms answering only this request.');
  });
})();
