/*
 * Browser client of the example application.
 *
 * Every action calls one of the registered services with fetch(), so the page is never reloaded:
 * only the result area changes. An HTTP error (such as the 404 of an unknown resource) is reported
 * differently from a network failure, because they are different problems.
 */
(() => {
  'use strict';

  const statusBox = document.getElementById('status');
  const resultBox = document.getElementById('result');
  const errorBox = document.getElementById('error');
  const buttons = Array.from(document.querySelectorAll('button'));

  function showLoading(label) {
    statusBox.textContent = 'Requesting ' + label + '…';
    statusBox.classList.add('loading');
    resultBox.hidden = true;
    errorBox.hidden = true;
    buttons.forEach((button) => { button.disabled = true; });
  }

  function showResult(url, status, text, elapsed) {
    statusBox.classList.remove('loading');
    statusBox.textContent = 'HTTP ' + status + ' from ' + url + ' in ' + elapsed + ' ms.';
    resultBox.textContent = text;
    resultBox.hidden = false;
    errorBox.hidden = true;
    buttons.forEach((button) => { button.disabled = false; });
  }

  function showError(title, detail) {
    statusBox.classList.remove('loading');
    statusBox.textContent = 'The last request did not succeed.';
    errorBox.textContent = detail ? title + ' — ' + detail : title;
    errorBox.hidden = false;
    resultBox.hidden = true;
    buttons.forEach((button) => { button.disabled = false; });
  }

  /** Calls one service and shows its status and body. */
  async function call(url, label) {
    showLoading(label);
    const startedAt = performance.now();

    let response;
    try {
      response = await fetch(url);
    } catch (networkFailure) {
      // No HTTP status exists here: the request never completed.
      showError('The server could not be reached',
        'The connection failed before any response arrived.');
      return;
    }

    const text = await response.text();
    const elapsed = Math.round(performance.now() - startedAt);

    // The status is inspected before the body is treated as a successful answer.
    if (!response.ok) {
      showError('HTTP ' + response.status + ' ' + response.statusText, text.trim());
      return;
    }
    showResult(url, response.status, text, elapsed);
  }

  document.getElementById('hello-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const name = document.getElementById('name').value.trim();
    call('/hello?name=' + encodeURIComponent(name), 'a greeting');
  });

  document.getElementById('square-form').addEventListener('submit', (event) => {
    event.preventDefault();
    const value = document.getElementById('value').value.trim();
    call('/square?value=' + encodeURIComponent(value), 'the square');
  });

  document.getElementById('pi-button').addEventListener('click', () => call('/pi', 'pi'));

  document.getElementById('greeting-button').addEventListener('click', () => {
    const name = document.getElementById('name').value.trim() || 'world';
    call('/greeting?name=' + encodeURIComponent(name) + '&language=en', 'the greeting service');
  });

  document.getElementById('config-button').addEventListener('click',
    () => call('/config', 'the configuration'));

  document.getElementById('notfound-button').addEventListener('click',
    () => call('/unknown', 'a resource that does not exist'));
})();
