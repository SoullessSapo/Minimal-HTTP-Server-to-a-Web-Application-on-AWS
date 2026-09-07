// Client side behaviour of the mini application.
// At this stage the script only proves that the browser asked for it as a separate request and
// that the server described it with a JavaScript content type.
document.addEventListener('DOMContentLoaded', () => {
  const status = document.getElementById('js-status');
  if (status) {
    status.textContent =
      'JavaScript was downloaded as a separate request and is running in the browser.';
  }
});
