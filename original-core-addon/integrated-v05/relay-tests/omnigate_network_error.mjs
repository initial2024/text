/**
 * No endpoint URLs, response bodies, headers, secrets or exception messages
 * are included in diagnostics. Only bounded known error names/codes are used.
 */
const allowedCodes=new Set([
  'ENOTFOUND','EAI_AGAIN','ECONNREFUSED','ECONNRESET','EHOSTUNREACH',
  'ENETUNREACH','ETIMEDOUT','EPIPE','UND_ERR_CONNECT_TIMEOUT',
  'UND_ERR_HEADERS_TIMEOUT','UND_ERR_BODY_TIMEOUT','UND_ERR_SOCKET',
  'CERT_HAS_EXPIRED','UNABLE_TO_VERIFY_LEAF_SIGNATURE',
  'UNABLE_TO_GET_ISSUER_CERT_LOCALLY','DEPTH_ZERO_SELF_SIGNED_CERT',
  'ERR_TLS_CERT_ALTNAME_INVALID','ERR_SSL_WRONG_VERSION_NUMBER',
  'ERR_TLS_HANDSHAKE_TIMEOUT','ERR_INVALID_REDIRECT'
]);
export function classifyFetchFailure(err) {
  const visited=new Set(), pending=[err];
  let code='UNKNOWN';
  for(let i=0;i<pending.length&&i<12;i++){
    const current=pending[i];
    if(!current||typeof current!=='object'||visited.has(current))continue;
    visited.add(current);
    if(typeof current.code==='string'&&allowedCodes.has(current.code)){
      code=current.code;break;
    }
    if(current.cause)pending.push(current.cause);
    if(Array.isArray(current.errors))pending.push(...current.errors.slice(0,5));
  }
  const name=err?.name;
  let classification='OTHER_NETWORK_OR_REDIRECT_FAILURE';
  if(code==='ENOTFOUND'||code==='EAI_AGAIN')classification='DNS_RESOLUTION_FAILED';
  else if(code.startsWith('CERT_')||code.startsWith('ERR_TLS_')||
          code.startsWith('ERR_SSL_')||code.startsWith('UNABLE_')||
          code.startsWith('DEPTH_ZERO_'))classification='TLS_CERTIFICATE_OR_HANDSHAKE_FAILED';
  else if(code.includes('TIMEOUT')||code==='ETIMEDOUT'||
          name==='TimeoutError'||name==='AbortError')classification='CONNECT_OR_READ_TIMEOUT';
  else if(code==='ECONNRESET'||code==='UND_ERR_SOCKET'||code==='EPIPE')
    classification='SOCKET_CLOSED_OR_RESET';
  else if(code==='ECONNREFUSED'||code==='EHOSTUNREACH'||code==='ENETUNREACH')
    classification='NETWORK_ROUTE_UNAVAILABLE';
  else if(code==='ERR_INVALID_REDIRECT')classification='REDIRECT_INVALID';
  return {classification,cause_code:code};
}

export function redirectSummary(response, expectedOrigin) {
  if(response.status<300||response.status>399)return {redirect_observed:false};
  const target=response.headers.get('location');
  let redirect_kind='MISSING_LOCATION';
  if(target) {
    try {
      const u=new URL(target,expectedOrigin);
      if(u.protocol!=='https:')redirect_kind='NON_HTTPS_REDIRECT';
      else redirect_kind=u.origin===expectedOrigin?'SAME_ORIGIN':'OTHER_ORIGIN';
    }catch{redirect_kind='INVALID_LOCATION';}
  }
  return {redirect_observed:true,redirect_kind};
}
