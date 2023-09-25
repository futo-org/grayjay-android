
class URL {
    constructor(url, base) {
        let baseParts;
        try {
            baseParts = URL.parse(base);
        }
        catch (e) {
            throw new Error('Invalid base URL');
        }
        let urlParts = URL.parse(url);
        if (urlParts.protocol) {
            this._parts = { ...urlParts };
        }
        else {
            this._parts = {
                protocol: baseParts.protocol,
                username: baseParts.username,
                password: baseParts.password,
                hostname: baseParts.hostname,
                port: baseParts.port,
                path: urlParts.path || baseParts.path,
                query: urlParts.query || baseParts.query,
                hash: urlParts.hash,
            };
        }
    }
    static init() {
        this.URLRegExp = new RegExp('^' + this.patterns.protocol + '?' + this.patterns.authority + '?' + this.patterns.path + this.patterns.query + '?' + this.patterns.hash + '?');
        this.AuthorityRegExp = new RegExp('^' + this.patterns.authentication + '?' + this.patterns.hostname + this.patterns.port + '?$');
    }
    static parse(url) {
        const urlMatch = this.URLRegExp.exec(url);
        if (urlMatch !== null) {
            const authorityMatch = urlMatch[2] ? this.AuthorityRegExp.exec(urlMatch[2]) : [null, null, null, null, null];
            if (authorityMatch !== null) {
                return {
                    protocol: urlMatch[1] || '',
                    username: authorityMatch[1] || '',
                    password: authorityMatch[2] || '',
                    hostname: authorityMatch[3] || '',
                    port: authorityMatch[4] || '',
                    path: urlMatch[3] || '',
                    query: urlMatch[4] || '',
                    hash: urlMatch[5] || '',
                };
            }
        }
        throw new Error('Invalid URL');
    }
    get hash() {
        return this._parts.hash;
    }
    set hash(value) {
        value = value.toString();
        if (value.length === 0) {
            this._parts.hash = '';
        }
        else {
            if (value.charAt(0) !== '#')
                value = '#' + value;
            this._parts.hash = encodeURIComponent(value);
        }
    }
    get host() {
        return this.hostname + (this.port ? (':' + this.port) : '');
    }
    set host(value) {
        value = value.toString();
        const url = new URL('http://' + value);
        this._parts.hostname = url.hostname;
        this._parts.port = url.port;
    }
    get hostname() {
        return this._parts.hostname;
    }
    set hostname(value) {
        value = value.toString();
        this._parts.hostname = encodeURIComponent(value);
    }
    get href() {
        const authentication = (this.username || this.password) ? (this.username + (this.password ? (':' + this.password) : '') + '@') : '';
        return this.protocol + '//' + authentication + this.host + this.pathname + this.search + this.hash;
    }
    set href(value) {
        value = value.toString();
        const url = new URL(value);
        this._parts = { ...url._parts };
    }
    get origin() {
        return this.protocol + '//' + this.host;
    }
    get password() {
        return this._parts.password;
    }
    set password(value) {
        value = value.toString();
        this._parts.password = encodeURIComponent(value);
    }
    get pathname() {
        return this._parts.path ? this._parts.path : '/';
    }
    set pathname(value) {
        let chunks = value.toString().split('/').map(encodePathSegment);
        if (chunks[0]) {
            // ensure joined string starts with slash.
            chunks.unshift('');
        }
        this._parts.path = chunks.join('/');
    }
    get port() {
        return this._parts.port;
    }
    set port(value) {
        let port = parseInt(value);
        if (isNaN(port)) {
            this._parts.port = '0';
        }
        else {
            this._parts.port = Math.max(0, port % (2 ** 16)).toString();
        }
    }
    get protocol() {
        return this._parts.protocol + ':';
    }
    set protocol(value) {
        value = value.toString();
        if (value.length !== 0) {
            if (value.charAt(value.length - 1) === ':') {
                value = value.slice(0, -1);
            }
            this._parts.protocol = encodeURIComponent(value);
        }
    }
    get search() {
        return this._parts.query;
    }
    set search(value) {
        value = value.toString();
        if (value.charAt(0) !== '?')
            value = '?' + value;
        this._parts.query = value;
    }
    get username() {
        return this._parts.username;
    }
    set username(value) {
        value = value.toString();
        this._parts.username = encodeURIComponent(value);
    }
    get searchParams() {
        const searchParams = new URLSearchParams(this.search);
        ['append', 'delete', 'set'].forEach((methodName) => {
            const method = searchParams[methodName];
            searchParams[methodName] = (...args) => {
                method.apply(searchParams, args);
                this.search = searchParams.toString();
            };
        });
        return searchParams;
    }
    toString() {
        return this.href;
    }
}

URL.patterns = {
    protocol: '(?:([^:/?#]+):)',
    authority: '(?://([^/?#]*))',
    path: '([^?#]*)',
    query: '(\\?[^#]*)',
    hash: '(#.*)',
    authentication: '(?:([^:]*)(?::([^@]*))?@)',
    hostname: '([^:]+)',
    port: '(?::(\\d+))',
};
URL.init();