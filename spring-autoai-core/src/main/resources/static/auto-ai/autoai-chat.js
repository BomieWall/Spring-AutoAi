// ============================================
// Built-in Frontend Tools Definition
// ============================================
export const BUILTIN_FRONTEND_TOOLS = [
  // localStorage operation tools
  {
    name: 'getLocalStorage',
    description: 'Retrieve data from localStorage, supports automatic JSON parsing',
    parameters: {
      type: 'object',
      properties: {
        key: {
          type: 'string',
          description: 'Storage key name',
          example: 'username'
        },
        parseJson: {
          type: 'boolean',
          description: 'Whether to parse the value as JSON object',
          example: false
        }
      },
      required: ['key']
    },
    fn: async (args) => {
      try {
        const value = localStorage.getItem(args.key);
        if (args.parseJson && value) {
          return JSON.parse(value);
        }
        return value;
      } catch (error) {
        return { error: 'Read failed: ' + error.message };
      }
    }
  },
  {
    name: 'setLocalStorage',
    description: 'Store data to localStorage, objects will be automatically converted to JSON strings',
    parameters: {
      type: 'object',
      properties: {
        key: {
          type: 'string',
          description: 'Storage key name',
          example: 'username'
        },
        value: {
          type: 'string',
          description: 'Value to store (objects will be automatically converted to JSON)',
          example: 'John'
        }
      },
      required: ['key', 'value']
    },
    fn: async (args) => {
      try {
        const value = typeof args.value === 'object'
          ? JSON.stringify(args.value)
          : args.value;

        localStorage.setItem(args.key, value);
        return { success: true, message: `Saved: ${args.key} = ${value}` };
      } catch (error) {
        console.error('setLocalStorage execution failed:', error);
        return { error: 'Save failed: ' + error.message };
      }
    }
  },
  {
    name: 'removeLocalStorage',
    description: 'Delete data with specified key from localStorage',
    parameters: {
      type: 'object',
      properties: {
        key: {
          type: 'string',
          description: 'Key name to delete',
          example: 'oldData'
        }
      },
      required: ['key']
    },
    fn: async (args) => {
      try {
        localStorage.removeItem(args.key);
        return { success: true, message: `Deleted: ${args.key}` };
      } catch (error) {
        return { error: 'Delete failed: ' + error.message };
      }
    }
  },
  {
    name: 'listLocalStorage',
    description: 'List all key-value pairs in localStorage',
    parameters: {
      type: 'object',
      properties: {},
      required: []
    },
    fn: async () => {
      try {
        const data = {};
        for (let i = 0; i < localStorage.length; i++) {
          const key = localStorage.key(i);
          data[key] = localStorage.getItem(key);
        }
        return { count: Object.keys(data).length, data };
      } catch (error) {
        return { error: 'Read failed: ' + error.message };
      }
    }
  },
  // Cookie operation tools
  {
    name: 'getCookie',
    description: 'Read Cookie value with specified name',
    parameters: {
      type: 'object',
      properties: {
        name: {
          type: 'string',
          description: 'Cookie name',
          example: 'sessionId'
        }
      },
      required: ['name']
    },
    fn: async (args) => {
      try {
        const name = args.name + '=';
        const cookies = document.cookie.split(';');
        for (let cookie of cookies) {
          cookie = cookie.trim();
          if (cookie.startsWith(name)) {
            return decodeURIComponent(cookie.substring(name.length));
          }
        }
        return null;
      } catch (error) {
        return { error: 'Read failed: ' + error.message };
      }
    }
  },
  {
    name: 'getAllCookies',
    description: 'Read all cookies on the page',
    parameters: {
      type: 'object',
      properties: {},
      required: []
    },
    fn: async () => {
      try {
        const cookies = {};
        document.cookie.split(';').forEach(cookie => {
          const [name, value] = cookie.trim().split('=');
          if (name && value) {
            cookies[name] = decodeURIComponent(value);
          }
        });
        return { count: Object.keys(cookies).length, cookies };
      } catch (error) {
        return { error: 'Read failed: ' + error.message };
      }
    }
  },
  // Page information tools
  {
    name: 'getPageInfo',
    description: 'Get basic information of the current page, including title, URL, domain, etc.',
    parameters: {
      type: 'object',
      properties: {},
      required: []
    },
    fn: async () => {
      return {
        title: document.title,
        url: window.location.href,
        domain: window.location.hostname,
        path: window.location.pathname,
        userAgent: navigator.userAgent,
        language: navigator.language,
        screenWidth: window.screen.width,
        screenHeight: window.screen.height
      };
    }
  },
  // User interaction tools
  {
    name: 'showNotification',
    description: 'Display a beautiful modal notification popup on the page',
    parameters: {
      type: 'object',
      properties: {
        message: {
          type: 'string',
          description: 'Notification message content',
          example: 'Operation successful!'
        },
        type: {
          type: 'string',
          description: 'Notification type',
          enum: ['info', 'success', 'warning', 'error'],
          example: 'info'
        },
        duration: {
          type: 'number',
          description: 'Auto close time (milliseconds), 0 means no auto close',
          example: 3000
        }
      },
      required: ['message']
    },
    fn: async (args) => {
      try {
        showModernNotification(args.message, args.type || 'info', args.duration);
        return { success: true, message: 'Notification displayed' };
      } catch (error) {
        return { error: 'Failed to display notification: ' + error.message };
      }
    }
  },
  // Page operation tools
  {
    name: 'refreshPage',
    description: 'Refresh the current page, reload page content',
    parameters: {
      type: 'object',
      properties: {
        forceReload: {
          type: 'boolean',
          description: 'Whether to force reload from server (bypass cache)',
          example: false
        }
      },
      required: []
    },
    fn: async (args) => {
      try {
        if (args.forceReload) {
          window.location.reload(true);
        } else {
          window.location.reload();
        }
        return { success: true, message: 'Page refreshed' };
      } catch (error) {
        return { error: 'Refresh failed: ' + error.message };
      }
    }
  }
];

// Helper function to display modal notification
function showModernNotification(message, type = 'info', duration = 3000) {
  // Create notification container if it doesn't exist
  let container = document.getElementById('autoai-notification-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'autoai-notification-container';
    container.style.cssText = `
      position: fixed;
      top: 16px;
      right: 16px;
      z-index: 10002;
      display: flex;
      flex-direction: column;
      gap: 8px;
      pointer-events: none;
    `;
    document.body.appendChild(container);
  }

  // Create notification element
  const notification = document.createElement('div');
  notification.className = `autoai-notification autoai-notification--${type}`;

  const typeConfig = {
    info: {
      color: '#1890ff',
      iconBg: '#e6f7ff',
      icon: `<svg viewBox="64 64 896 896" width="16" height="16" fill="currentColor">
        <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm0 820c-205.4 0-372-166.6-372-372s166.6-372 372-372 372 166.6 372 372-166.6 372-372 372z"/>
        <path d="M464 336a48 48 0 1096 0 48 48 0 10-96 0zm72 112h-48c-4.4 0-8 3.6-8 8v272c0 4.4 3.6 8 8 8h48c4.4 0 8-3.6 8-8V456c0-4.4-3.6-8-8-8z"/>
      </svg>`
    },
    success: {
      color: '#52c41a',
      iconBg: '#f6ffed',
      icon: `<svg viewBox="64 64 896 896" width="16" height="16" fill="currentColor">
        <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm193.5 301.7l-210.6 292a31.8 31.8 0 01-51.7 0L318.5 484.9c-3.8-5.3 0-12.7 6.5-12.7h46.9c10.2 0 19.9 4.9 25.9 13.3l71.2 98.8 157.2-218c6-8.3 15.6-13.3 25.9-13.3H699c6.5 0 10.3 7.4 6.5 12.7z"/>
      </svg>`
    },
    warning: {
      color: '#faad14',
      iconBg: '#fffbe6',
      icon: `<svg viewBox="64 64 896 896" width="16" height="16" fill="currentColor">
        <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm-32 232c0-4.4 3.6-8 8-8h48c4.4 0 8 3.6 8 8v272c0 4.4-3.6 8-8 8h-48c-4.4 0-8-3.6-8-8V296zm32 440a48.01 48.01 0 010-96 48.01 48.01 0 010 96z"/>
      </svg>`
    },
    error: {
      color: '#ff4d4f',
      iconBg: '#fff1f0',
      icon: `<svg viewBox="64 64 896 896" width="16" height="16" fill="currentColor">
        <path d="M512 64C264.6 64 64 264.6 64 512s200.6 448 448 448 448-200.6 448-448S759.4 64 512 64zm165.4 618.2l-66-.3L512 563.4l-99.3 118.4-66.1.3c-4.4 0-8-3.5-8-8 0-1.9.7-3.7 1.9-5.2l130.1-155L340.5 359a8.32 8.32 0 01-1.9-5.2c0-4.4 3.6-8 8-8l66.1.3L512 464.6l99.3-118.4 66-.3c4.4 0 8 3.5 8 8 0 1.9-.7 3.7-1.9 5.2L553.5 514l130 155c1.2 1.5 1.9 3.3 1.9 5.2 0 4.4-3.6 8-8 8z"/>
      </svg>`
    }
  };

  const config = typeConfig[type] || typeConfig.info;

  notification.style.cssText = `
    pointer-events: auto;
    min-width: 320px;
    max-width: 480px;
    background: #ffffff;
    border-radius: 8px;
    padding: 12px 16px;
    box-shadow: 0 6px 16px 0 rgba(0, 0, 0, 0.08),
                0 3px 6px -4px rgba(0, 0, 0, 0.12),
                0 9px 28px 8px rgba(0, 0, 0, 0.05);
    display: flex;
    align-items: flex-start;
    gap: 12px;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
    font-size: 14px;
    line-height: 1.5715;
    color: rgba(0, 0, 0, 0.85);
    border-left: 4px solid ${config.color};
    opacity: 0;
    transform: translateX(100%);
    transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
    position: relative;
    overflow: hidden;
  `;

  // Add close button
  const closeBtn = document.createElement('button');
  closeBtn.setAttribute('aria-label', 'Close');
  closeBtn.innerHTML = `<svg viewBox="64 64 896 896" width="12" height="12" fill="currentColor">
    <path d="M563.8 512l262.5-312.9c4.4-5.2.4-13.1-6.1-13.1h-79.8c-4.7 0-9.2 2.1-12.3 5.7L511.6 449.8 295.1 191.7c-3-3.6-7.5-5.7-12.3-5.7H203c-6.5 0-10.5 7.8-6.1 13.1L459.4 512 196.9 824.9A7.95 7.95 0 00203 838h79.8c4.7 0 9.2-2.1 12.3-5.7l216.5-258.1 216.5 258.1c3 3.6 7.5 5.7 12.3 5.7h79.8c6.5 0 10.5-7.8 6.1-13.1L563.8 512z"/>
  </svg>`;
  closeBtn.style.cssText = `
    flex-shrink: 0;
    background: transparent;
    border: none;
    color: rgba(0, 0, 0, 0.45);
    cursor: pointer;
    padding: 4px;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: color 0.2s ease;
    border-radius: 4px;
  `;
  closeBtn.onmouseenter = () => closeBtn.style.color = 'rgba(0, 0, 0, 0.75)';
  closeBtn.onmouseleave = () => closeBtn.style.color = 'rgba(0, 0, 0, 0.45)';

  // Icon container
  const iconContainer = document.createElement('div');
  iconContainer.style.cssText = `
    flex-shrink: 0;
    width: 24px;
    height: 24px;
    background: ${config.iconBg};
    border-radius: 50%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: ${config.color};
  `;
  iconContainer.innerHTML = config.icon;

  // Content container
  const contentContainer = document.createElement('div');
  contentContainer.style.cssText = `
    flex: 1;
    min-width: 0;
    word-wrap: break-word;
    padding-right: 4px;
    white-space: pre-wrap;
    line-height: 1.6;
  `;
  contentContainer.textContent = message;

  notification.appendChild(iconContainer);
  notification.appendChild(contentContainer);
  notification.appendChild(closeBtn);
  container.appendChild(notification);

  // Add progress bar (if auto close is set)
  let progressBar = null;
  if (duration > 0) {
    progressBar = document.createElement('div');
    progressBar.style.cssText = `
      position: absolute;
      bottom: 0;
      left: 0;
      height: 3px;
      background: ${config.color};
      width: 100%;
      transform-origin: left;
      transition: transform ${duration}ms linear;
    `;
    notification.appendChild(progressBar);

    // Start animation on next frame
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        progressBar.style.transform = 'scaleX(0)';
      });
    });
  }

  // Trigger enter animation
  requestAnimationFrame(() => {
    notification.style.opacity = '1';
    notification.style.transform = 'translateX(0)';
  });

  // Close function
  const closeNotification = () => {
    notification.style.opacity = '0';
    notification.style.transform = 'translateX(100%)';
    setTimeout(() => {
      if (notification.parentNode) {
        notification.parentNode.removeChild(notification);
      }
      if (container.children.length === 0) {
        container.remove();
      }
    }, 300);
  };

  // Click close button
  closeBtn.onclick = (e) => {
    e.stopPropagation();
    closeNotification();
  };

  // Pause auto close when mouse hovers (if there's a progress bar)
  if (progressBar) {
    notification.onmouseenter = () => {
      progressBar.style.transition = 'none';
      progressBar.style.transform = 'scaleX(1)';
    };
    notification.onmouseleave = () => {
      progressBar.style.transition = `transform ${duration}ms linear`;
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          progressBar.style.transform = 'scaleX(0)';
        });
      });
    };
  }

  // Auto close
  if (duration > 0) {
    setTimeout(closeNotification, duration);
  }
}

// AutoAi Chat Client: Encapsulates OpenAI-compatible interface requests.
export class AutoAiChatClient {
  constructor({ baseUrl = "", headers = {}, environmentContext = [] } = {}) {
    this.baseUrl = baseUrl.replace(/\/$/, "");
    this.customHeaders = headers; // Can be object or function
    this.environmentContext = environmentContext; // Environment info array, can be array or function
    this.sessionId = this.generateSessionId();
  }

  // Generate session ID
  generateSessionId() {
    return 'session_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
  }

  // Reset session
  resetSession() {
    this.sessionId = this.generateSessionId();
  }

  // Update custom headers
  setHeaders(headers) {
    this.customHeaders = { ...this.getCustomHeaders(), ...headers };
  }

  // Get custom headers (supports object or function)
  getCustomHeaders() {
    if (typeof this.customHeaders === 'function') {
      return this.customHeaders() || {};
    }
    return this.customHeaders || {};
  }

  // Set environment context
  setEnvironmentContext(context) {
    this.environmentContext = context;
  }

  // Get environment context (supports array or function)
  getEnvironmentContext() {
    if (typeof this.environmentContext === 'function') {
      return this.environmentContext() || [];
    }
    return this.environmentContext || [];
  }

  // Get complete request headers (merge default headers and custom headers)
  getRequestHeaders() {
    return {
      "Content-Type": "application/json",
      ...this.getCustomHeaders()
    };
  }

  // Initiate streaming chat request - use simplified custom format
  async chatStream(messages, signal, onChunk, onComplete, onError, frontendTools) {
    const payload = {
      messages,
      stream: true,
      sessionId: this.sessionId,
      environment_context: this.getEnvironmentContext(), // Add environment info
      frontend_tools: frontendTools || [] // Add frontend tool definitions
    };

    let hasCompleted = false;
    let reader = null;

    // Set up overall timeout detection (slightly longer than backend to give backend time to send error messages)
    const overallTimeout = setTimeout(() => {
      if (!hasCompleted && reader) {
        console.warn('Detected long unresponsiveness, may have timed out');
        // Cancel request
        if (signal) {
          signal.dispatchEvent(new Event('abort'));
        }
        if (!hasCompleted) {
          hasCompleted = true;
          onError && onError(new Error('Request timed out, no response received'));
        }
      }
    }, 610000); // 10 minutes 10 seconds, slightly longer than backend's 10 minutes

    try {
      const response = await fetch(`${this.baseUrl}/v1/chat/stream`, {
        method: "POST",
        headers: this.getRequestHeaders(),
        body: JSON.stringify(payload),
        signal: signal, // Add abort signal
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(`HTTP ${response.status}: ${errorText}`);
      }

      reader = response.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let taskId = null;

      // No longer set data receive timeout detection, let backend control connection disconnect
      // This can support long-running ACTIONS (like database queries, file processing, etc.)
      // Connection can only be disconnected through the following ways:
      // 1. Backend sends completion marker [DONE]
      // 2. Backend sends error message
      // 3. User manually clicks "Stop thinking" button
      // 4. Backend overall timeout (10 minutes)
      // 5. Network truly disconnects

      let shouldBreak = false; // Mark whether to break out of while loop

      try {
        while (true) {
          const { done, value } = await reader.read();
          if (done) break;

          buffer += decoder.decode(value, { stream: true });

          // Process SSE format data stream
          const lines = buffer.split('\n');
          buffer = lines.pop() || ''; // Keep last line (may be incomplete)

          for (const line of lines) {
            const trimmedLine = line.trim();

            // Skip empty lines and non-data lines
            if (!trimmedLine || !trimmedLine.startsWith('data:')) {
              continue;
            }

            // Check if it's the end marker
            if (trimmedLine === 'data:[DONE]') {
              hasCompleted = true;
              shouldBreak = true;
              break; // Break out of for loop
            }

            // Extract JSON data
            const jsonStr = trimmedLine.substring(5); // Remove 'data:' prefix

            try {
              const message = JSON.parse(jsonStr);

              // Process task info
              if (message.type === 'TASK_INFO' && message.taskId) {
                taskId = message.taskId;
                console.log('Received task ID:', taskId);
                continue;
              }

              // Process error message
              if (message.type === 'ERROR') {
                hasCompleted = true;
                shouldBreak = true;
                onError && onError(new Error(message.content || 'An error occurred'));
                break; // Break out of for loop
              }

              // Process simplified message format {"content": "...", "type": "THINKING"}
              if (message.content !== undefined && message.type !== undefined) {
                // console.log('Received message:', message);

                // Call callback function, pass content, type and taskId
                onChunk && onChunk(message.content, message.type, taskId);
              }
            } catch (e) {
              console.warn('Failed to parse message:', jsonStr, e);
            }
          }

          // If received end marker or error, break out of while loop
          if (shouldBreak) {
            break;
          }
        }
      } finally {
        // Clean up timer
        // clearInterval(dataTimeout);
        clearTimeout(overallTimeout);
      }

      // Normal completion
      hasCompleted = true;
      onComplete && onComplete();

    } catch (error) {
      if (hasCompleted) return; // If already processed error or completed, ignore

      hasCompleted = true;
      clearTimeout(overallTimeout);

      // If actively aborted, don't show error
      if (error.name === 'AbortError') {
        console.log('Request has been aborted');
        return;
      }

      // Handle network error or read error
      console.error('Stream request error:', error);
      onError && onError(error);
    }
  }
}

// AutoAi Chat Widget: Provides basic chat UI and message management.
export class AutoAiChatWidget {
  constructor({ container, baseUrl = "", headers = {}, environmentContext = [], title = null, subtitle = null, healthCheckInterval = 30000, frontendTools = [] } = {}) {
    // Store default title/subtitle keys to use after language loading
    this.defaultTitleKey = title ? null : "widget.title";
    this.defaultSubtitleKey = subtitle ? null : "widget.subtitle";
    this.title = title || "Spring-AutoAi Chat Console";
    this.subtitle = subtitle || "OpenAI Compatible Protocol · ReAct Tool Chain · Streaming Output";
    this.baseUrl = baseUrl;
    this.healthCheckInterval = healthCheckInterval;
    this.client = new AutoAiChatClient({ baseUrl, headers, environmentContext }); // Pass environment info
    this.messages = [];
    this.healthCheckTimer = null;
    this.isSidebarMode = !container; // If no container specified, use sidebar mode
    this.currentTaskId = null; // ID of current task
    this.abortController = null; // Used to terminate fetch requests

    // New: Frontend tool management
    this.frontendTools = new Map();  // toolName -> {fn, definition}

    // 1. Register built-in tools first
    this.registerFrontendTools(BUILTIN_FRONTEND_TOOLS);

    // 2. Then register user-defined tools (can override built-in tools)
    this.registerFrontendTools(frontendTools);

    // Load language resources
    this.loadLanguageResources();

    if (container) {
      // Embedded mode: Use specified container
      this.container = container;
      this.render();
      this.startHealthCheck();
    } else {
      // Sidebar mode: Automatically create sidebar and floating button
      this.createSidebar();
      this.startHealthCheck();
    }
  }

  /**
   * Load language resources from lan.js
   */
  async loadLanguageResources() {
    try {
      const lanUrl = `${this.baseUrl}/lan.js`;
      const response = await fetch(lanUrl);
      if (response.ok) {
        const script = await response.text();
        // Execute the script to populate window.LANG
        eval(script);

        // Update title/subtitle with localized versions if using defaults
        if (this.defaultTitleKey) {
          this.title = this.t(this.defaultTitleKey);
          const titleEl = this.container.querySelector('.autoai-chat__title');
          if (titleEl) titleEl.textContent = this.title;
        }
        if (this.defaultSubtitleKey) {
          this.subtitle = this.t(this.defaultSubtitleKey);
          const subtitleEl = this.container.querySelector('.autoai-chat__subtitle');
          if (subtitleEl) subtitleEl.textContent = this.subtitle;
        }

        // Update tool descriptions for registered tools
        this.updateToolDescriptions();

        // Update button texts and other UI elements
        this.updateUITexts();
      }
    } catch (error) {
      console.warn('Failed to load language resources:', error);
      // Set default language if load fails
      window.LANG = {};
    }
  }

  /**
   * Update tool descriptions with localized texts
   */
  updateToolDescriptions() {
    for (const [name, tool] of this.frontendTools) {
      // Update description if a translation key exists
      const key = `tool.${name}.description`;
      if (window.LANG && window.LANG[key]) {
        tool.definition.function.description = window.LANG[key];
      }
    }
  }

  /**
   * Update UI texts with localized versions
   */
  updateUITexts() {
    // Update send button
    const sendBtn = this.container.querySelector('.autoai-chat__send');
    if (sendBtn) {
      sendBtn.textContent = this.t('widget.send_button');
    }

    // Update stop thinking button
    const abortBtn = this.container.querySelector('.autoai-chat__abort');
    if (abortBtn) {
      abortBtn.textContent = this.t('widget.stop_thinking_button');
    }

    // Update placeholder
    const input = this.container.querySelector('.autoai-chat__text');
    if (input) {
      input.placeholder = this.t('widget.placeholder');
    }

    // Update new session button
    const newSessionBtn = this.container.querySelector('.autoai-chat__clear-btn');
    if (newSessionBtn) {
      newSessionBtn.textContent = this.t('widget.new_session');
    }
  }

  /**
   * Get translated text by key
   * @param {string} key - Translation key (e.g., "widget.title")
   * @param {...any} args - Arguments for string formatting
   * @returns {string} Translated text
   */
  t(key, ...args) {
    if (!window.LANG) {
      return key;
    }
    const text = window.LANG[key];
    if (text === undefined) {
      return key;
    }
    // Replace placeholders like {0}, {1} with arguments
    return args.reduce((result, arg, index) => {
      return result.replace(`{${index}}`, arg);
    }, text);
  }

  // Set environment context
  setEnvironmentContext(context) {
    this.client.setEnvironmentContext(context);
  }

  // Get environment context
  getEnvironmentContext() {
    return this.client.getEnvironmentContext();
  }

  /**
   * Register frontend tools
   * @param {Array} tools - Tool definition array
   * Each tool format:
   * {
   *   name: "getUsername",           // Tool name (unique)
   *   description: "Get current username",  // Tool description
   *   parameters: {                  // Parameter definition (JSON Schema format)
   *     type: "object",
   *     properties: {
   *       format: { type: "string", description: "Return format" }
   *     }
   *   },
   *   fn: async (args) => {          // Tool execution function
   *     return localStorage.getItem("username");
   *   }
   * }
   */
  registerFrontendTools(tools) {
    if (!Array.isArray(tools)) return;

    for (const tool of tools) {
      if (tool.name && typeof tool.fn === 'function') {
        this.frontendTools.set(tool.name, {
          fn: tool.fn,
          definition: {
            type: "function",
            function: {
              name: tool.name,
              description: tool.description || "",
              parameters: tool.parameters || { type: "object", properties: {} }
            }
          }
        });
      }
    }
  }

  /**
   * Get frontend tool definition list (for sending to backend)
   */
  getFrontendToolsDefinition() {
    const tools = [];
    for (const [, tool] of this.frontendTools) {
      tools.push(tool.definition);
    }
    return tools;
  }

  /**
   * Handle frontend tool call
   */
  async handleFrontendToolCall(toolCall, callId) {
    const toolName = toolCall.function?.name;
    const args = toolCall.function?.arguments;

    // Parse arguments: arguments may be JSON string or parsed object
    let parsedArgs;
    if (typeof args === 'string') {
      try {
        parsedArgs = JSON.parse(args);
      } catch (e) {
        console.error('Failed to parse arguments:', e, 'Original arguments:', args);
        return this.sendToolResult(callId, null, this.t('tool.invalid_arguments', e.message), true);
      }
    } else {
      parsedArgs = args;
    }

    console.log(`Calling frontend tool: ${toolName}`, parsedArgs);

    const tool = this.frontendTools.get(toolName);
    if (!tool) {
      console.error(`Frontend tool not found: ${toolName}`);
      return this.sendToolResult(callId, null, this.t("tool.not_found"), true);
    }

    try {
      const result = await tool.fn(parsedArgs);
      this.sendToolResult(callId, result, null, false);
    } catch (error) {
      this.sendToolResult(callId, null, error.message, true);
    }
  }

  /**
   * Send tool execution result to backend
   */
  async sendToolResult(callId, result, error, isError) {
    const response = await fetch(`${this.baseUrl}/v1/chat/tool-result`, {
      method: 'POST',
      headers: this.client.getRequestHeaders(),
      body: JSON.stringify({
        sessionId: this.client.sessionId,
        toolCall: {
          callId,
          result,
          error,
          isError
        }
      })
    });

    if (!response.ok) {
      console.error('Failed to send tool result:', response.status);
    }
  }

  // Render UI structure and styles.
  render() {
    this.container.innerHTML = "";
    this.container.style.height = "100%";
    const wrapper = document.createElement("div");
    wrapper.className = "autoai-chat";

    wrapper.innerHTML = `
      <div class="autoai-chat__shell">
        <header class="autoai-chat__header">
          <div>
            <div class="autoai-chat__title">${this.title}</div>
            <div class="autoai-chat__subtitle">${this.subtitle}</div>
          </div>
          <div class="autoai-chat__header-actions">
            <button class="autoai-chat__clear-btn">New Session</button>
            <div class="autoai-chat__badge" id="autoai-status-badge">Checking...</div>
          </div>
        </header>
        <section class="autoai-chat__messages"></section>
        <footer class="autoai-chat__input">
          <input class="autoai-chat__text" placeholder="Enter your question, I'll help you complete it." />
          <button class="autoai-chat__abort" style="display: none;">Stop Thinking</button>
          <button class="autoai-chat__send">Send</button>
        </footer>
      </div>
    `;

    const style = document.createElement("style");
    style.textContent = `
      .autoai-chat {
        font-family: Arial, sans-serif;
        height: 100%;
        display: flex;
        flex-direction: column;
      }
      .autoai-chat__shell {
        flex: 1;
        display: flex;
        flex-direction: column;
        background: rgba(255, 255, 255, 0.9);
        border-radius: 24px;
        backdrop-filter: blur(14px);
        animation: fadeUp 0.6s ease-out;
        box-sizing: border-box;
        min-height: 0;
        padding: 24px;
      }
      .autoai-chat__header {
        display: flex;
        align-items: center;
        justify-content: space-between;
        border-bottom: 1px solid rgba(50, 47, 75, 0.1);
        padding-bottom: 16px;
        flex-shrink: 0;
      }
      .autoai-chat__header-actions {
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .autoai-chat__clear-btn {
        font-size: 13px;
        padding: 6px 12px;
        border: 1px solid #123b3a;
        border-radius: 999px;
        background: transparent;
        color: #123b3a;
        cursor: pointer;
        transition: all 0.2s ease;
        letter-spacing: 1px;
      }
      .autoai-chat__clear-btn:hover {
        background: #123b3a;
        color: #fef5e3;
        transform: translateY(-1px);
      }
      .autoai-chat__title {
        font-size: 16px;
        font-weight: 700;
        color: #123b3a;
        letter-spacing: 1px;
      }
      .autoai-chat__subtitle {
        color: #3e5a58;
        font-size: 13px;
        margin-top: 6px;
      }
      .autoai-chat__badge {
        font-size: 13px;
        padding: 6px 12px;
        border-radius: 999px;
        background: #123b3a;
        color: #fef5e3;
        letter-spacing: 2px;
        transition: all 0.3s ease;
      }
      .autoai-chat__badge--online {
        background: #059669;
        color: #fff;
      }
      .autoai-chat__badge--offline {
        background: #dc2626;
        color: #fff;
      }
      .autoai-chat__badge--checking {
        background: #6b7280;
        color: #fff;
      }
      .autoai-chat__messages {
        flex: 1;
        margin: 24px 0;
        display: flex;
        flex-direction: column;
        gap: 16px;
        overflow-y: auto;
        padding-right: 8px;
        min-height: 0;
      }
      .autoai-chat__message {
        padding: 8px 12px;
        border-radius: 16px;
        line-height: 1.6;
        font-size: 13px;
        animation: popIn 0.4s ease-out;
        max-width: 95%;
      }
      .autoai-chat__message--user {
        align-self: flex-end;
        background: #123b3a;
        color: #fef5e3;
        box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
        max-width: 80%;
      }
      .autoai-chat__message--ai {
         color: #2a3f3e;
      }
      .autoai-chat__message--system {
        align-self: center;
        background: #f8f9fa;
        color: #6c757d;
        font-size: 13px;
        padding: 8px 12px;
        border-radius: 12px;
        border: 1px solid #e9ecef;
        max-width: 200px;
        text-align: center;
      }
      .autoai-chat__block {
        margin: 8px 0;
        border-radius: 8px;
        overflow: hidden;
        box-shadow: 0 2px 4px rgba(0,0,0,0.1);
      }
      .autoai-chat__block--thinking {
        background: linear-gradient(135deg, #e0f2fe, #b3e5fc);
        border-left: 4px solid #0891b2;
      }
      .autoai-chat__block--reasoning {
        background: linear-gradient(135deg, #f3f4f6, #e5e7eb);
        border-left: 4px solid #6b7280;
      }
      .autoai-chat__block--action {
        background: linear-gradient(135deg, #fff7ed, #fed7aa);
        border-left: 4px solid #d97706;
      }
      .autoai-chat__block--observation {
        background: linear-gradient(135deg, #f3e8ff, #ddd6fe);
        border-left: 4px solid #7c3aed;
      }
      .autoai-chat__block--answer {
        background: linear-gradient(135deg, #ecfdf5, #bbf7d0);
        border-left: 4px solid #059669;
      }
      .autoai-chat__block--ask {
        background: linear-gradient(135deg, #fffbeb, #fde68a);
        border-left: 4px solid #f59e0b;
      }
      .autoai-chat__block--content {
        background: #f8fafc;
        border-left: 4px solid #64748b;
      }
      .autoai-chat__block--error {
        background: linear-gradient(135deg, #fef2f2, #fecaca);
        border-left: 4px solid #dc2626;
      }
      .autoai-chat__block-title {
        font-weight: 600;
        font-size: 13px;
        padding: 8px 14px 4px 14px;
        opacity: 0.8;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
      .autoai-chat__block-inline-title {
        font-weight: 600;
        font-size: 13px;
        opacity: 0.8;
        text-transform: uppercase;
        letter-spacing: 0.5px;
        display: inline;
        margin-right: 4px;
      }
      .autoai-chat__block-content {
        padding: 4px 14px 12px 14px;
        line-height: 1.5;
        position: relative;
      }
      .autoai-chat__block--action .autoai-chat__block-content,
      .autoai-chat__block--observation .autoai-chat__block-content {
        padding: 8px 14px;
        display: flex;
        align-items: flex-start;
        flex-wrap: wrap;
      }
      .autoai-chat__code-block {
        background: #1e1e1e;
        border: 1px solid #333;
        border-radius: 8px;
        padding: 0;
        margin: 12px 0;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
        font-size: 13px;
        line-height: 1.5;
        overflow-x: auto;
        white-space: pre-wrap;
        word-wrap: break-word;
        position: relative;
      }
      .autoai-chat__code-block code {
        display: block;
        padding: 12px 16px;
        background: none;
        border: none;
        font-family: inherit;
        color: #d4d4d4;
      }
      .autoai-chat__code-lang {
        background: #333;
        color: #9ca3af;
        font-size: 11px;
        padding: 4px 12px;
        border-radius: 8px 8px 0 0;
        text-transform: uppercase;
        letter-spacing: 0.5px;
      }
      /* Markdown styles */
      .autoai-md-heading {
        margin: 12px 0 8px 0;
        font-weight: 600;
        color: #1f2937;
        line-height: 1.3;
      }
      h1.autoai-md-heading { font-size: 1.5em; border-bottom: 1px solid #e5e7eb; padding-bottom: 6px; }
      h2.autoai-md-heading { font-size: 1.3em; }
      h3.autoai-md-heading { font-size: 1.15em; }
      h4.autoai-md-heading { font-size: 1.05em; }
      h5.autoai-md-heading { font-size: 1em; }
      h6.autoai-md-heading { font-size: 0.95em; color: #6b7280; }
      .autoai-md-list-item {
        margin: 4px 0;
        padding-left: 8px;
      }
      .autoai-md-quote {
        border-left: 3px solid #d1d5db;
        padding-left: 12px;
        margin: 8px 0;
        color: #6b7280;
        font-style: italic;
      }
      .autoai-md-hr {
        border: none;
        border-top: 1px solid #e5e7eb;
        margin: 16px 0;
      }
      .autoai-md-inline-code {
        background: #f3f4f6;
        color: #dc2626;
        padding: 2px 6px;
        border-radius: 4px;
        font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', 'Consolas', monospace;
        font-size: 0.9em;
      }
      .autoai-md-link {
        color: #2563eb;
        text-decoration: none;
        border-bottom: 1px solid transparent;
        transition: border-color 0.2s;
      }
      .autoai-md-link:hover {
        border-bottom-color: #2563eb;
      }
      /* Table container style - supports horizontal scrolling */
      .autoai-md-table-wrapper {
        width: 100%;
        overflow-x: auto;
        margin: 12px 0;
        border-radius: 8px;
        box-shadow: 0 1px 3px rgba(0,0,0,0.1);
      }
      /* Table style */
      .autoai-md-table {
        min-width: 100%;
        width: max-content;
        border-collapse: collapse;
        font-size: 13px;
        background: #fff;
      }
      .autoai-md-table th,
      .autoai-md-table td {
        padding: 10px 14px;
        text-align: left;
        border-bottom: 1px solid #e5e7eb;
        white-space: nowrap;
      }
      .autoai-md-table th {
        background: #f9fafb;
        font-weight: 600;
        color: #374151;
        position: sticky;
        top: 0;
      }
      .autoai-md-table tr:last-child td {
        border-bottom: none;
      }
      .autoai-md-table tr:hover td {
        background: #f9fafb;
      }
      /* Stream preview styles */
      .stream-preview {
        opacity: 0.85;
      }
      .rendered-content {
        line-height: 1.6;
      }
      .partial-content {
        opacity: 0.7;
        animation: pulse 1.5s ease-in-out infinite;
      }
      .autoai-chat__chunk--thinking {
        color: #0891b2;
        font-style: italic;
      }
      .autoai-chat__chunk--action {
        color: #d97706;
        font-weight: 500;
      }
      .autoai-chat__chunk--observation {
        color: #7c3aed;
        font-size: 0.9em;
      }
      .autoai-chat__chunk--answer {
        color: #059669;
        font-weight: 600;
      }
      .autoai-chat__chunk--ask {
        color: #f59e0b;
        font-weight: 600;
      }
      .autoai-chat__chunk--error {
        color: #dc2626;
        font-weight: 500;
      }
      .autoai-chat__typing-indicator-wrapper {
        display: inline-flex;
        align-items: center;
        gap: 4px;
        padding: 6px 12px;
        margin: 6px 0;
      }
      .autoai-chat__typing-dot {
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #64748b;
        animation: typingBounce 1.4s ease-in-out infinite;
      }
      .autoai-chat__typing-indicator {
        display: inline-block;
        width: 8px;
        height: 8px;
        border-radius: 50%;
        background: #64748b;
        margin-left: 4px;
        animation: typing 1.4s ease-in-out infinite;
      }
      @keyframes typingBounce {
        0%, 60%, 100% {
          transform: translateY(0);
          opacity: 0.7;
        }
        30% {
          transform: translateY(-8px);
          opacity: 1;
        }
      }
      @keyframes pulse {
        0%, 100% { opacity: 0.7; }
        50% { opacity: 1; }
      }
      @keyframes typing {
        0%, 60%, 100% { transform: translateY(0); }
        30% { transform: translateY(-10px); }
      }
      .autoai-chat__input {
        display: flex;
        gap: 12px;
        flex-shrink: 0;
      }
      .autoai-chat__text {
        flex: 1;
        border: none;
        border-radius: 999px;
        padding: 12px 18px;
        background: #f2fbf6;
        font-size: 13px;
        outline: none;
      }
      .autoai-chat__send {
        border: none;
        border-radius: 999px;
        padding: 12px 22px;
        background: linear-gradient(135deg, #0f3d3e, #1f6b5b);
        color: #fff;
        font-size: 13px;
        cursor: pointer;
        transition: transform 0.2s ease;
      }
      .autoai-chat__send:hover {
        transform: translateY(-2px);
      }
      .autoai-chat__send:disabled {
        opacity: 0.6;
        cursor: not-allowed;
        transform: none;
      }
      .autoai-chat__abort {
        border: none;
        border-radius: 999px;
        padding: 12px 22px;
        background: linear-gradient(135deg, #dc2626, #b91c1c);
        color: #fff;
        font-size: 13px;
        cursor: pointer;
        transition: transform 0.2s ease;
      }
      .autoai-chat__abort:hover {
        transform: translateY(-2px);
        background: linear-gradient(135deg, #b91c1c, #991b1b);
      }
      .autoai-chat__abort:disabled {
        opacity: 0.6;
        cursor: not-allowed;
        transform: none;
      }
      // @keyframes fadeUp {
      //   from { opacity: 0; transform: translateY(20px); }
      //   to { opacity: 1; transform: translateY(0); }
      // }
      // @keyframes popIn {
      //   from { opacity: 0; transform: scale(0.96); }
      //   to { opacity: 1; transform: scale(1); }
      // }
      // @media (max-width: 600px) {
      //   .autoai-chat__shell {
      //     padding: 20px;
      //   }
      //   .autoai-chat__message {
      //     max-width: 100%;
      //   }
      // }
    `;

    this.container.appendChild(style);
    this.container.appendChild(wrapper);

    this.messageContainer = wrapper.querySelector(".autoai-chat__messages");
    this.input = wrapper.querySelector(".autoai-chat__text");
    this.sendButton = wrapper.querySelector(".autoai-chat__send");
    this.abortButton = wrapper.querySelector(".autoai-chat__abort");
    this.clearButton = wrapper.querySelector(".autoai-chat__clear-btn");
    this.statusBadge = wrapper.querySelector("#autoai-status-badge");

    this.sendButton.addEventListener("click", () => this.handleSend());
    this.abortButton.addEventListener("click", () => this.handleAbort());
    this.clearButton.addEventListener("click", () => this.handleClearSession());
    this.input.addEventListener("keydown", (event) => {
      if (event.key === "Enter") {
        this.handleSend();
      }
    });
  }

  // Start health check
  startHealthCheck() {
    // Execute once immediately
    this.checkHealth();

    // Periodic check
    this.healthCheckTimer = setInterval(() => {
      this.checkHealth();
    }, this.healthCheckInterval);
  }

  // Stop health check
  stopHealthCheck() {
    if (this.healthCheckTimer) {
      clearInterval(this.healthCheckTimer);
      this.healthCheckTimer = null;
    }
  }

  // Check backend health status
  async checkHealth() {
    this.updateStatusBadge('checking', 'Checking...');

    try {
      // Create timeout controller
      const controller = new AbortController();
      const timeoutId = setTimeout(() => controller.abort(), 5000);

      // Construct health check URL - use same path logic as chatStream
      const healthUrl = this.baseUrl ? `${this.baseUrl}/v1/health` : '/v1/health';

      const response = await fetch(healthUrl, {
        method: 'GET',
        headers: this.client.getRequestHeaders(), // Use custom headers
        signal: controller.signal
      });

      clearTimeout(timeoutId);

      if (response.ok) {
        const data = await response.json();
        if (data.status === 'online') {
          this.updateStatusBadge('online', 'ONLINE');
        } else {
          this.updateStatusBadge('offline', 'OFFLINE');
        }
      } else {
        this.updateStatusBadge('offline', 'OFFLINE');
      }
    } catch (error) {
      console.error('Health check failed:', error);
      this.updateStatusBadge('offline', 'OFFLINE');
    }
  }

  // Update status badge
  updateStatusBadge(status, text) {
    if (!this.statusBadge) return;

    // Remove all status classes
    this.statusBadge.classList.remove('autoai-chat__badge--online', 'autoai-chat__badge--offline', 'autoai-chat__badge--checking');

    // Add new status class
    this.statusBadge.classList.add(`autoai-chat__badge--${status}`);
    this.statusBadge.textContent = text;
  }

  // Destroy component, clean up resources
  destroy() {
    this.stopHealthCheck();
    if (this.isSidebarMode) {
      // Clean up sidebar mode
      if (this.floatingButton) {
        this.floatingButton.remove();
      }
      if (this.sidebar) {
        this.sidebar.remove();
      }
      if (this.overlay) {
        this.overlay.remove();
      }
      if (this.tooltip) {
        this.tooltip.remove();
      }
    } else if (this.container) {
      this.container.innerHTML = "";
    }
  }

  // Create sidebar mode
  createSidebar() {
    // Create overlay
    this.overlay = document.createElement('div');
    this.overlay.className = 'autoai-sidebar__overlay';
    this.overlay.style.cssText = `
      position: fixed;
      top: 0;
      left: 0;
      right: 0;
      bottom: 0;
      background: rgba(0, 0, 0, 0.5);
      z-index: 9998;
      opacity: 0;
      visibility: hidden;
      transition: all 0.3s ease;
    `;
    this.overlay.addEventListener('click', () => this.closeSidebar());
    document.body.appendChild(this.overlay);

    // Create sidebar container
    this.sidebar = document.createElement('div');
    this.sidebar.className = 'autoai-sidebar';
    this.sidebar.style.cssText = `
      position: fixed;
      top: 0;
      right: 0;
      width: 45%;
      min-width: 450px;
      max-width: 100vw;
      height: 100vh;
      background: white;
      z-index: 10001;
      transform: translateX(100%);
      transition: transform 0.3s ease;
      box-shadow: -5px 0 20px rgba(0, 0, 0, 0.1);
    `;
    document.body.appendChild(this.sidebar);

    // Use sidebar as container to render chat component
    this.container = this.sidebar;
    this.render();


    // Create floating button
    this.floatingButton = document.createElement('button');
    this.floatingButton.className = 'autoai-floating-button';
    this.floatingButton.setAttribute('aria-label', 'Open AI Assistant');
    this.floatingButton.innerHTML = `
      <svg width="24" height="24" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M21 15C21 15.5304 20.7893 16.0391 20.4142 16.4142C20.0391 16.7893 19.5304 17 19 17H7L3 21V5C3 4.46957 3.21071 3.96086 3.58579 3.58579C3.96086 3.21071 4.46957 3 5 3H19C19.5304 3 20.0391 3.21071 20.4142 3.58579C20.7893 3.96086 21 4.46957 21 5V15Z" stroke="white" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        <circle cx="8" cy="9" r="1" fill="white"/>
        <circle cx="12" cy="9" r="1" fill="white"/>
        <circle cx="16" cy="9" r="1" fill="white"/>
      </svg>
    `;
    this.floatingButton.style.cssText = `
      position: fixed;
      bottom: 24px;
      right: 24px;
      width: 56px;
      height: 56px;
      border: none;
      background: #1a1a1a;
      border-radius: 16px;
      cursor: pointer;
      box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
      z-index: 10000;
      transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 0;
    `;
    this.floatingButton.addEventListener('mouseenter', () => {
      this.floatingButton.style.transform = 'translateY(-2px)';
      this.floatingButton.style.boxShadow = '0 8px 30px rgba(0, 0, 0, 0.25)';
      this.floatingButton.style.background = '#333';
    });
    this.floatingButton.addEventListener('mouseleave', () => {
      this.floatingButton.style.transform = 'translateY(0)';
      this.floatingButton.style.boxShadow = '0 4px 20px rgba(0, 0, 0, 0.15)';
      this.floatingButton.style.background = '#1a1a1a';
    });
    this.floatingButton.addEventListener('click', () => this.openSidebar());
    document.body.appendChild(this.floatingButton);

    // Create tooltip text (optional, shown on first display)
    this.tooltip = document.createElement('div');
    this.tooltip.className = 'autoai-floating-tooltip';
    this.tooltip.textContent = 'AI Assistant';
    this.tooltip.style.cssText = `
      position: fixed;
      bottom: 32px;
      right: 88px;
      background: #1a1a1a;
      color: white;
      padding: 8px 16px;
      border-radius: 8px;
      font-size: 14px;
      font-weight: 500;
      white-space: nowrap;
      z-index: 10000;
      opacity: 0;
      transform: translateX(10px);
      transition: all 0.3s ease;
      pointer-events: none;
      box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
    `;

    // Add small arrow
    const arrow = document.createElement('div');
    arrow.style.cssText = `
      position: absolute;
      right: -6px;
      top: 50%;
      transform: translateY(-50%);
      width: 0;
      height: 0;
      border-top: 6px solid transparent;
      border-bottom: 6px solid transparent;
      border-left: 6px solid #1a1a1a;
    `;
    this.tooltip.appendChild(arrow);
    document.body.appendChild(this.tooltip);

    // Show tooltip on mouse hover
    this.floatingButton.addEventListener('mouseenter', () => {
      this.tooltip.style.opacity = '1';
      this.tooltip.style.transform = 'translateX(0)';
    });
    this.floatingButton.addEventListener('mouseleave', () => {
      this.tooltip.style.opacity = '0';
      this.tooltip.style.transform = 'translateX(10px)';
    });

    // Hide tooltip when sidebar opens
    const originalOpenSidebar = this.openSidebar.bind(this);
    this.openSidebar = () => {
      originalOpenSidebar();
      this.tooltip.style.opacity = '0';
    };
  }

  // Open sidebar
  openSidebar() {
    if (this.sidebar) {
      this.sidebar.style.transform = 'translateX(0)';
    }
    if (this.overlay) {
      this.overlay.style.opacity = '1';
      this.overlay.style.visibility = 'visible';
    }
  }

  // Close sidebar
  closeSidebar() {
    if (this.sidebar) {
      this.sidebar.style.transform = 'translateX(100%)';
    }
    if (this.overlay) {
      this.overlay.style.opacity = '0';
      this.overlay.style.visibility = 'hidden';
    }
  }

  // Handle clear session
  handleClearSession() {
    // Clear frontend message history
    this.messages = [];
    this.messageContainer.innerHTML = "";

    // Reset client session ID
    this.client.resetSession();

    // Show session reset message
    this.showSystemMessage("New session started");
  }

  // Show system message
  showSystemMessage(message) {
    const systemBubble = document.createElement("div");
    systemBubble.className = "autoai-chat__message autoai-chat__message--system";
    systemBubble.textContent = message;
    this.messageContainer.appendChild(systemBubble);
    this.scrollToBottom();
  }

  // Handle sending messages and response writing.
  async handleSend() {
    const text = this.input.value.trim();
    if (!text) {
      return;
    }

    this.appendMessage("user", text);
    this.input.value = "";
    this.sendButton.disabled = true;
    this.sendButton.textContent = this.t("widget.thinking");

    // Show abort button
    this.abortButton.style.display = "block";
    this.abortButton.disabled = false;

    // Create new AbortController
    this.abortController = new AbortController();

    // Directly use streaming response, as the backend /v1/chat/stream endpoint is designed for streaming
    await this.handleStreamResponse();
  }

  // Handle streaming response
  async handleStreamResponse() {
    // Initialize state, but don't create message element immediately
    this.initializeStreamState();
    this.pendingMessageElement = null; // Used to delay message element creation

    // Immediately create message element and show initial loading animation
    this.pendingMessageElement = this.createMessageElement("assistant");
    this.messageContainer.appendChild(this.pendingMessageElement);
    this.addTypingIndicator(this.pendingMessageElement);
    this.scrollToBottom();

    try {
      let fullContent = "";

      await this.client.chatStream(
        this.messages,
        this.abortController.signal,
        // onChunk - Receive simplified format content and type
        (content, type, taskId) => {
          // Save taskId
          if (taskId) {
            this.currentTaskId = taskId;
          }

          // Check if it's a frontend tool call notification
          if (content && content.includes('[FRONTEND_TOOL_CALL]')) {
            try {
              const jsonStr = content.replace('[FRONTEND_TOOL_CALL]', '').trim();
              const notification = JSON.parse(jsonStr);
              if (notification.type === 'FRONTEND_TOOL_CALL') {
                // Handle frontend tool call
                this.handleFrontendToolCall(notification.toolCall, notification.callId);
                return; // Don't show tool call notification, just execute tool
              }
            } catch (e) {
              console.error('Failed to parse frontend tool call notification:', e);
            }
          }

          this.processStreamMessage(this.pendingMessageElement, content, type);
          fullContent += content;
          this.scrollToBottom();
        },
        // onComplete
        () => {
          // Process remaining content
          if (this.pendingMessageElement) {
            this.flushContentBuffer(this.pendingMessageElement);
          }

          this.messages.push({ role: "assistant", content: fullContent });
          this.resetSendButton();
          this.cleanupStreamState();
        },
        // onError
        (error) => {
          // If message element hasn't been created yet, create one now to display error
          if (!this.pendingMessageElement) {
            this.pendingMessageElement = this.createMessageElement("assistant");
            this.messageContainer.appendChild(this.pendingMessageElement);
          }

          this.showStreamError(this.pendingMessageElement, error.message);
          this.resetSendButton();
          this.cleanupStreamState();
        },
        // Pass frontend tool definitions
        this.getFrontendToolsDefinition()
      );

    } catch (error) {
      // If message element hasn't been created yet, create one now to display error
      if (!this.pendingMessageElement) {
        this.pendingMessageElement = this.createMessageElement("assistant");
        this.messageContainer.appendChild(this.pendingMessageElement);
      }

      this.showStreamError(this.pendingMessageElement, error.message);
      this.resetSendButton();
      this.cleanupStreamState();
    }
  }

  // Initialize streaming state
  initializeStreamState() {
    this.contentBuffer = '';
    this.blockContentBuffer = ''; // Content buffer of current block
    this.currentBlock = null;
    this.currentBlockType = null;
    this.lastProcessedIndex = 0;
    this.firstBlockCreated = false;
    this.typingIndicator = null;
  }

  // Clean up streaming state
  cleanupStreamState() {
    this.contentBuffer = '';
    this.blockContentBuffer = '';
    this.currentBlock = null;
    this.currentBlockType = null;
    this.lastProcessedIndex = 0;
    this.pendingMessageElement = null;
    this.firstBlockCreated = false;
    this.removeTypingIndicator();
    this.stopActionTimer();  // Clear timer
  }

  // Reset send button
  resetSendButton() {
    this.sendButton.disabled = false;
    this.sendButton.textContent = this.t("widget.send_button");
    // Hide abort button
    this.abortButton.style.display = "none";
    this.abortButton.disabled = true;
    // Clear taskId
    this.currentTaskId = null;
    // Clear abortController
    this.abortController = null;
  }

  // Handle abort inference
  async handleAbort() {
    if (!this.currentTaskId || this.abortButton.disabled) {
      return;
    }

    try {
      // Disable abort button to prevent repeated clicks
      this.abortButton.disabled = true;
      this.abortButton.textContent = this.t("widget.aborting");
      
      // 1. Terminate frontend fetch request
      if (this.abortController) {
        this.abortController.abort();
      }

      // 2. Call backend abort API
      const abortUrl = this.baseUrl ? `${this.baseUrl}/v1/chat/stream/${this.currentTaskId}` : `/v1/chat/stream/${this.currentTaskId}`;
      const response = await fetch(abortUrl, {
        method: 'DELETE',
        headers: this.client.getRequestHeaders()
      });

      if (response.ok) {
        console.log('Task successfully terminated');
      } else {
        console.warn('Failed to terminate task:', response.status);
      }

    } catch (error) {
      console.error('Error occurred while terminating task:', error);
    } finally {
      // Clean up streaming state (including removing typing indicator)
      this.cleanupStreamState();
      // Reset UI
      this.resetSendButton();
      // Show system message
      this.showSystemMessage(this.t("react.aborted"));
    }
  }

  // Process streaming message - accumulate content, render when type changes
  processStreamMessage(messageElement, content, type) {
    const typeMapping = {
      'THINKING': 'thinking',
      'REASONING': 'reasoning',
      'ACTION': 'action',
      'ACTION_START': 'action_start',  // New: action start type
      'ACTION_END': 'action_end',      // New: action end type
      'OBSERVATION': 'observation',
      'ANSWER': 'answer',
      'ASK': 'ask',
      'CONTENT': 'content',
      'ERROR': 'error'
    };

    const frontendType = typeMapping[type] || 'content';

    // Special handling for ACTION_START - display execution timer
    if (frontendType === 'action_start') {
      this.startActionTimer(messageElement);
      return;
    }

    // Special handling for ACTION_END - stop timer based on execution result
    if (frontendType === 'action_end') {
      const isSuccess = content === 'success';
      this.stopActionTimer(isSuccess);
      return;
    }

    // If observation result received, stop timer (default to success)
    // if (frontendType === 'observation') {
    //   this.stopActionTimer(true);
    // }

    // If type changes
    if (this.currentBlockType !== frontendType) {
      // Render previously accumulated content first
      if (this.currentBlock && this.blockContentBuffer) {
        this.renderFinalContent(this.currentBlock, this.blockContentBuffer);
        this.blockContentBuffer = '';
      }
      // Create new block
      this.createNewBlock(messageElement, frontendType);
    }

    // Accumulate content to buffer
    if (content !== undefined && content !== null) {
      if (!this.blockContentBuffer) {
        this.blockContentBuffer = '';
      }
      this.blockContentBuffer += content;

      // Display plain text preview in real-time
      this.showRawPreview(this.currentBlock, this.blockContentBuffer);
    }
  }

  // Start execution timer
  startActionTimer(messageElement) {
    // Clear previous timer
    this.stopActionTimer();

    // If current block is not action, create one first
    if (this.currentBlockType !== 'action') {
      // Render previously accumulated content first
      if (this.currentBlock && this.blockContentBuffer) {
        this.renderFinalContent(this.currentBlock, this.blockContentBuffer);
        this.blockContentBuffer = '';
      }
      this.createNewBlock(messageElement, 'action');
    }

    // Add timer in current action block
    const contentDiv = this.currentBlock;
    const timerDisplay = document.createElement('div');
    timerDisplay.className = 'action-timer-display';
    timerDisplay.style.cssText = `
      display: flex;
      align-items: center;
      gap: 8px;
      width: 100%;
      color: #6b7280;
    `;

    // Add spinning icon
    const spinner = document.createElement('span');
    spinner.innerHTML = `
      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
        <circle cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4" stroke-opacity="0.3"/>
        <path d="M12 2a10 10 0 0 1 10 10" stroke="currentColor" stroke-width="4" stroke-linecap="round">
          <animateTransform attributeName="transform" type="rotate" from="0 12 12" to="360 12 12" dur="1s" repeatCount="indefinite"/>
        </path>
      </svg>
    `;
    timerDisplay.appendChild(spinner);

    // Add timer text
    const timerText = document.createElement('span');
    timerText.className = 'action-timer-text';
    timerText.textContent = 'Executing... 0.0s';
    timerDisplay.appendChild(timerText);
    contentDiv.appendChild(timerDisplay);
    this.scrollToBottom();

    // Save timer information
    this.actionTimer = {
      startTime: Date.now(),
      element: timerDisplay,
      textElement: timerText,
      intervalId: setInterval(() => {
        const elapsed = (Date.now() - this.actionTimer.startTime) / 1000;
        this.actionTimer.textElement.textContent = `Executing... ${elapsed.toFixed(1)}s`;
      }, 100)
    };
  }

  // Stop execution timer
  stopActionTimer(isSuccess = true) {
    if (this.actionTimer) {
      clearInterval(this.actionTimer.intervalId);

      // Display final execution time and status
      const elapsed = (Date.now() - this.actionTimer.startTime) / 1000;
      const timerDisplay = this.actionTimer.element;
      if (timerDisplay) {
        if (isSuccess) {
          timerDisplay.innerHTML = `
            <span style="color: #059669;">✓ ${this.t("widget.execution_success")}</span>
            <span style="color: #6b7280; margin-left: 8px;">${this.t("block_label.execution_time", elapsed.toFixed(1))}</span>
          `;
        } else {
          timerDisplay.innerHTML = `
            <span style="color: #dc2626;">✗ ${this.t("widget.execution_failed")}</span>
            <span style="color: #6b7280; margin-left: 8px;">${this.t("block_label.execution_time", elapsed.toFixed(1))}</span>
          `;
        }
      }

      this.actionTimer = null;
    }
  }

  // Display raw text preview (during streaming)
  showRawPreview(blockElement, content) {
    if (!blockElement) return;

    // Clear previous preview content
    const existingPreview = blockElement.querySelector('.stream-preview');
    if (existingPreview) {
      existingPreview.remove();
    }

    // Create preview container
    const preview = document.createElement('div');
    preview.className = 'stream-preview';
    preview.style.whiteSpace = 'pre-wrap';
    preview.style.wordBreak = 'break-word';
    preview.textContent = content;

    blockElement.appendChild(preview);
  }

  // Render final content (Markdown formatted)
  renderFinalContent(blockElement, content) {
    if (!blockElement || !content) return;

    // Clear preview content
    const existingPreview = blockElement.querySelector('.stream-preview');
    if (existingPreview) {
      existingPreview.remove();
    }

    // Clear previous rendered content
    const existingRendered = blockElement.querySelector('.rendered-content');
    if (existingRendered) {
      existingRendered.remove();
    }

    // Create render container
    const rendered = document.createElement('div');
    rendered.className = 'rendered-content';

    // Parse and render Markdown
    this.parseAndRenderMarkdown(rendered, content);

    blockElement.appendChild(rendered);
  }

  // Parse and render Markdown content
  parseAndRenderMarkdown(container, content) {
    // Split by lines for processing
    const lines = content.split('\n');
    let i = 0;
    let lastWasBlock = false; // Mark whether previous element was a block-level element (code block, table, etc.)

    while (i < lines.length) {
      const line = lines[i];

      // Skip empty lines (but record state)
      if (!line.trim()) {
        // If previous element wasn't block-level, and not consecutive empty lines, add line break
        if (!lastWasBlock && i > 0 && lines[i-1].trim()) {
          container.appendChild(document.createElement('br'));
        }
        lastWasBlock = false;
        i++;
        continue;
      }

      // Detect code block start ```
      if (line.trim().startsWith('```')) {
        const lang = line.trim().substring(3).trim();
        const codeLines = [];
        i++;

        // Collect code block content until end marker
        while (i < lines.length && !lines[i].trim().startsWith('```')) {
          codeLines.push(lines[i]);
          i++;
        }
        i++; // Skip closing ```

        // Render code block
        this.renderCodeBlock(container, codeLines.join('\n'), lang);
        lastWasBlock = true;
        continue;
      }

      // Detect table (lines starting with |)
      if (line.trim().startsWith('|')) {
        const tableLines = [line];
        i++;

        // Collect table rows
        while (i < lines.length && lines[i].trim().startsWith('|')) {
          tableLines.push(lines[i]);
          i++;
        }

        // Render table
        this.renderTable(container, tableLines);
        lastWasBlock = true;
        continue;
      }

      // Process headings # ## ### etc.
      const headingMatch = line.match(/^(#{1,6})\s+(.+)$/);
      if (headingMatch) {
        const level = headingMatch[1].length;
        const text = headingMatch[2];
        const heading = document.createElement(`h${level}`);
        heading.className = 'autoai-md-heading';
        this.renderInlineFormats(heading, text);
        container.appendChild(heading);
        lastWasBlock = true;
        i++;
        continue;
      }

      // Process unordered list - * +
      const ulMatch = line.match(/^[\s]*[-*+]\s+(.+)$/);
      if (ulMatch) {
        const listItem = document.createElement('div');
        listItem.className = 'autoai-md-list-item';
        const bullet = document.createTextNode('• ');
        listItem.appendChild(bullet);
        this.renderInlineFormats(listItem, ulMatch[1]);
        container.appendChild(listItem);
        lastWasBlock = false;
        i++;
        continue;
      }

      // Process ordered list 1. 2. etc.
      const olMatch = line.match(/^[\s]*(\d+)\.\s+(.+)$/);
      if (olMatch) {
        const listItem = document.createElement('div');
        listItem.className = 'autoai-md-list-item';
        const num = document.createTextNode(`${olMatch[1]}. `);
        listItem.appendChild(num);
        this.renderInlineFormats(listItem, olMatch[2]);
        container.appendChild(listItem);
        lastWasBlock = false;
        i++;
        continue;
      }

      // Process blockquote >
      const quoteMatch = line.match(/^>\s*(.*)$/);
      if (quoteMatch) {
        const quote = document.createElement('div');
        quote.className = 'autoai-md-quote';
        this.renderInlineFormats(quote, quoteMatch[1]);
        container.appendChild(quote);
        lastWasBlock = false;
        i++;
        continue;
      }

      // Process horizontal rule --- *** ___
      if (/^[-*_]{3,}$/.test(line.trim())) {
        const hr = document.createElement('hr');
        hr.className = 'autoai-md-hr';
        container.appendChild(hr);
        lastWasBlock = true;
        i++;
        continue;
      }

      // Regular paragraph
      const p = document.createElement('span');
      this.renderInlineFormats(p, line);
      container.appendChild(p);

      // If next line is also regular text, add line break
      if (i < lines.length - 1 && lines[i + 1].trim() &&
          !lines[i + 1].trim().startsWith('```') &&
          !lines[i + 1].trim().startsWith('|') &&
          !lines[i + 1].match(/^#{1,6}\s/) &&
          !lines[i + 1].match(/^[-*_]{3,}$/)) {
        container.appendChild(document.createElement('br'));
      }

      lastWasBlock = false;
      i++;
    }
  }

  // Render table
  renderTable(container, tableLines) {
    if (tableLines.length < 2) {
      // Not a valid table, process as regular text
      tableLines.forEach(line => {
        const span = document.createElement('span');
        span.textContent = line;
        container.appendChild(span);
        container.appendChild(document.createElement('br'));
      });
      return;
    }

    // Create table container (supports horizontal scrolling)
    const tableWrapper = document.createElement('div');
    tableWrapper.className = 'autoai-md-table-wrapper';

    const table = document.createElement('table');
    table.className = 'autoai-md-table';

    // Parse table header
    const headerCells = this.parseTableRow(tableLines[0]);
    const thead = document.createElement('thead');
    const headerRow = document.createElement('tr');
    headerCells.forEach(cell => {
      const th = document.createElement('th');
      this.renderInlineFormats(th, cell.trim());
      headerRow.appendChild(th);
    });
    thead.appendChild(headerRow);
    table.appendChild(thead);

    // Skip separator row (second row is usually |---|---|)
    let startRow = 1;
    if (tableLines.length > 1 && /^[\s|:-]+$/.test(tableLines[1])) {
      startRow = 2;
    }

    // Parse data rows
    const tbody = document.createElement('tbody');
    for (let i = startRow; i < tableLines.length; i++) {
      const cells = this.parseTableRow(tableLines[i]);
      const row = document.createElement('tr');
      cells.forEach(cell => {
        const td = document.createElement('td');
        this.renderInlineFormats(td, cell.trim());
        row.appendChild(td);
      });
      tbody.appendChild(row);
    }
    table.appendChild(tbody);

    tableWrapper.appendChild(table);
    container.appendChild(tableWrapper);
  }

  // Parse table row
  parseTableRow(line) {
    // Remove leading and trailing |
    let trimmed = line.trim();
    if (trimmed.startsWith('|')) trimmed = trimmed.substring(1);
    if (trimmed.endsWith('|')) trimmed = trimmed.substring(0, trimmed.length - 1);

    return trimmed.split('|');
  }

  // Add content to block - simplified version (no longer used)
  addContentToBlock(blockElement, content) {
    // Replaced by processStreamMessage
  }

  // Render formatted content - simplified to direct accumulation (no longer used)
  renderFormattedContent(blockElement, content) {
    // Replaced by renderFinalContent
  }

  // Render inline formats (bold, italic, code, links, etc.)
  renderInlineFormats(element, text) {
    if (!text) return;
    
    const patterns = [
      // Links [text](url)
      { regex: /\[([^\]]+)\]\(([^)]+)\)/g, handler: (match, text, url) => {
        const link = document.createElement('a');
        link.href = url;
        link.textContent = text;
        link.className = 'autoai-md-link';
        link.target = '_blank';
        link.rel = 'noopener noreferrer';
        return link;
      }},
      // Inline code `code`
      { regex: /`([^`]+)`/g, handler: (match, code) => {
        const codeEl = document.createElement('code');
        codeEl.className = 'autoai-md-inline-code';
        codeEl.textContent = code;
        return codeEl;
      }},
      // Bold italic ***text*** or ___text___
      { regex: /\*\*\*(.+?)\*\*\*|___(.+?)___/g, handler: (match, t1, t2) => {
        const el = document.createElement('strong');
        const em = document.createElement('em');
        em.textContent = t1 || t2;
        el.appendChild(em);
        return el;
      }},
      // Bold **text** or __text__
      { regex: /\*\*(.+?)\*\*|__(.+?)__/g, handler: (match, t1, t2) => {
        const el = document.createElement('strong');
        el.textContent = t1 || t2;
        return el;
      }},
      // Italic *text* or _text_
      { regex: /\*(.+?)\*|_(.+?)_/g, handler: (match, t1, t2) => {
        const el = document.createElement('em');
        el.textContent = t1 || t2;
        return el;
      }},
      // Strikethrough ~~text~~
      { regex: /~~(.+?)~~/g, handler: (match, text) => {
        const el = document.createElement('del');
        el.textContent = text;
        return el;
      }}
    ];

    // Collect all matching items
    let matches = [];
    for (const pattern of patterns) {
      let match;
      const regex = new RegExp(pattern.regex.source, pattern.regex.flags);
      while ((match = regex.exec(text)) !== null) {
        matches.push({
          start: match.index,
          end: match.index + match[0].length,
          match: match,
          handler: pattern.handler
        });
      }
    }

    // Sort by position, handle overlap
    matches.sort((a, b) => a.start - b.start);
    const filteredMatches = [];
    let lastEnd = 0;
    for (const m of matches) {
      if (m.start >= lastEnd) {
        filteredMatches.push(m);
        lastEnd = m.end;
      }
    }
    
    // Render content
    let lastIndex = 0;
    for (const m of filteredMatches) {
      if (m.start > lastIndex) {
        const textNode = document.createTextNode(text.substring(lastIndex, m.start));
        element.appendChild(textNode);
      }
      const formattedEl = m.handler(...m.match);
      element.appendChild(formattedEl);
      lastIndex = m.end;
    }
    
    if (lastIndex < text.length) {
      const textNode = document.createTextNode(text.substring(lastIndex));
      element.appendChild(textNode);
    }
  }

  // Render code block
  renderCodeBlock(container, codeContent, language = '') {
    const codeBlock = document.createElement('pre');
    codeBlock.className = 'autoai-chat__code-block';
    if (language) {
      codeBlock.setAttribute('data-language', language);
    }
    
    // Add language label
    if (language) {
      const langLabel = document.createElement('div');
      langLabel.className = 'autoai-chat__code-lang';
      langLabel.textContent = language;
      codeBlock.appendChild(langLabel);
    }

    const codeElement = document.createElement('code');
    codeElement.textContent = codeContent;
    codeBlock.appendChild(codeElement);

    container.appendChild(codeBlock);
  }

  // Add text with line breaks - simplified version (retained for compatibility)
  addTextWithLineBreaks(blockElement, content) {
    if (content === undefined || content === null) return;

    const lines = content.split('\n');
    for (let i = 0; i < lines.length; i++) {
      const line = lines[i];

      if (line.length > 0) {
        const textNode = document.createTextNode(line);
        blockElement.appendChild(textNode);
      }

      if (i < lines.length - 1) {
        const br = document.createElement("br");
        blockElement.appendChild(br);
      }
    }
  }

  // Display partial content (real-time update) - simplified version
  displayPartialContent(blockElement, content) {
    if (!content.trim() || !blockElement) return;

    // Clear previous partial content
    this.clearPartialContent();

    // Create partial content container
    const span = document.createElement('span');
    span.className = 'partial-content';
    span.textContent = content;

    blockElement.appendChild(span);
  }

  // Clear partial content
  clearPartialContent() {
    if (this.currentBlock) {
      const partialSpans = this.currentBlock.querySelectorAll('.partial-content');
      partialSpans.forEach(span => span.remove());
    }
  }

  // Flush content buffer - render final content when stream ends
  flushContentBuffer(messageElement) {
    // Render accumulated content of current block
    if (this.currentBlock && this.blockContentBuffer) {
      this.renderFinalContent(this.currentBlock, this.blockContentBuffer);
      this.blockContentBuffer = '';
    }
    this.clearPartialContent();
  }

  // Detect content type
  detectContentType(content) {
    const line = content.trim();

    // Detect emoji identifiers (already added by backend)
    if (line.includes("🤔") || line.includes("Thinking:") || line.includes("Thinking：")) {
      return "thinking";
    } else if (line.includes("⚡") || line.includes("Action:") || line.includes("Action：")) {
      return "action";
    } else if (line.includes("👁️") || line.includes("Observation:") || line.includes("Observation：")) {
      return "observation";
    } else if (line.includes("✅") && (line.includes("Answer:") || line.includes("Answer："))) {
      return "answer";
    } else if (line.includes("Tool call successful") || line.includes("✅ Tool call successful")) {
      return "observation"; // Tool call success belongs to observation results
    }

    // If no clear type identifier, continue using current type or default type
    return this.currentBlockType || "content";
  }

  // Ensure correct block
  ensureBlock(messageElement, type) {
    // If current block type is already target type, continue using it
    if (this.currentBlock && this.currentBlockType === type) {
      return;
    }

    // Create new block
    this.createNewBlock(messageElement, type);
  }

  // Create new content block - simplified version
  createNewBlock(messageElement, type) {
    // Clear partial content
    this.clearPartialContent();

    // Remove old typing indicator
    this.removeTypingIndicator();

    // Create new block
    const blockDiv = document.createElement("div");
    blockDiv.className = `autoai-chat__block autoai-chat__block--${type}`;

    // Simplified title mapping
    const titleMap = {
      'thinking': this.t('block_label.thinking'),
      'reasoning': this.t('block_label.reasoning'),
      'action': this.t('block_label.action'),
      'observation': this.t('block_label.observation'),
      'answer': this.t('block_label.answering'),
      'ask': this.t('block_label.ask'),
      'content': this.t('block_label.content'),
      'error': this.t('block_label.error')
    };

    // Create content container
    const contentDiv = document.createElement("div");
    contentDiv.className = "autoai-chat__block-content";

    // Use title layout uniformly
    if (titleMap[type]) {
      const title = document.createElement("div");
      title.className = "autoai-chat__block-title";
      title.textContent = titleMap[type];
      blockDiv.appendChild(title);
    }

    blockDiv.appendChild(contentDiv);

    // Add to message element
    messageElement.appendChild(blockDiv);

    // Set as current active block
    this.currentBlock = contentDiv;
    this.currentBlockType = type;
    this.firstBlockCreated = true;

    // Add typing indicator below current block
    this.addTypingIndicator(messageElement);
  }

  // Add line break
  addLineBreak() {
    if (this.currentBlock) {
      const br = document.createElement("br");
      this.currentBlock.appendChild(br);
    }
  }

  // Show stream error
  showStreamError(messageElement, errorMessage) {
    this.createNewBlock(messageElement, 'error');
    const textNode = document.createTextNode(this.t('widget.request_failed', errorMessage));
    this.currentBlock.appendChild(textNode);
  }

  // Create message element
  createMessageElement(role) {
    const bubble = document.createElement("div");
    bubble.className = `autoai-chat__message ${role === "user" ? "autoai-chat__message--user" : "autoai-chat__message--ai"}`;
    return bubble;
  }

  // Scroll to bottom
  scrollToBottom() {
    this.messageContainer.scrollTop = this.messageContainer.scrollHeight;
  }

  // Add typing indicator
  addTypingIndicator(messageElement) {
    // Remove old indicator
    this.removeTypingIndicator();

    // Create new indicator
    const indicator = document.createElement("div");
    indicator.className = "autoai-chat__typing-indicator-wrapper";

    // Add three dots
    for (let i = 0; i < 3; i++) {
      const dot = document.createElement("span");
      dot.className = "autoai-chat__typing-dot";
      dot.style.animationDelay = `${i * 0.16}s`;
      indicator.appendChild(dot);
    }

    messageElement.appendChild(indicator);
    this.typingIndicator = indicator;
    this.scrollToBottom();
  }

  // Remove typing indicator
  removeTypingIndicator() {
    if (this.typingIndicator && this.typingIndicator.parentNode) {
      this.typingIndicator.parentNode.removeChild(this.typingIndicator);
      this.typingIndicator = null;
    }
  }

  // Append message bubble and refresh scroll.
  appendMessage(role, content) {
    const message = { role, content };
    this.messages.push(message);

    const bubble = document.createElement("div");
    bubble.className = `autoai-chat__message ${role === "user" ? "autoai-chat__message--user" : "autoai-chat__message--ai"}`;
    bubble.textContent = content;
    this.messageContainer.appendChild(bubble);
    this.scrollToBottom();
  }
}