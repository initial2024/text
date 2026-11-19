import {createHash} from 'node:crypto';

export class SseReceiptAudit {
  constructor() {
    this.decoder=new TextDecoder('utf-8',{fatal:true});
    this.partial='';
    this.data=[];
    this.errors=[];
    this.started=performance.now();
    this.events=0;
    this.contentFrames=0;
    this.contentChars=0;
    this.toolFrames=0;
    this.finishes=[];
    this.doneCount=0;
    this.firstContentMs=null;
    this.hash=createHash('sha256');
    this.ended=false;
    this.eventLines=0;
  }
  feed(bytes) {
    if(this.ended)throw Error('AUDIT_ALREADY_FINISHED');
    let decoded;
    try{decoded=this.decoder.decode(bytes,{stream:true});}
    catch {this.fail('MALFORMED_UTF8');return;}
    this.addText(decoded);
  }
  addText(text) {
    this.partial+=text;
    if(this.partial.length>262144){this.fail('OVERSIZE_SSE_LINE');return;}
    let end;
    while((end=this.partial.indexOf('\n'))!==-1) {
      let line=this.partial.slice(0,end);
      this.partial=this.partial.slice(end+1);
      if(line.endsWith('\r'))line=line.slice(0,-1);
      if(line==='')this.dispatch();
      else if(line.startsWith(':')){} // SSE comments are keepalives.
      else if(line.startsWith('data:')) {
        this.data.push(line.slice(5).replace(/^ /,''));
        this.eventLines++;
        if(this.eventLines>300||this.data.join('\n').length>262144){
          this.fail('OVERSIZE_SSE_EVENT');this.data=[];
        }
      } else if(line.startsWith('event:')||line.startsWith('id:')||
               line.startsWith('retry:')){} // Do not treat metadata as content.
      else this.fail('UNEXPECTED_SSE_FIELD');
    }
  }
  fail(code){if(!this.errors.includes(code))this.errors.push(code);}
  dispatch(){
    if(!this.data.length){this.eventLines=0;return;}
    const raw=this.data.join('\n');
    this.data=[];this.eventLines=0;
    this.events++;
    if(raw==='[DONE]'){
      this.doneCount++;
      if(this.doneCount>1)this.fail('DUPLICATE_DONE');
      return;
    }
    if(this.doneCount>0)this.fail('DATA_AFTER_DONE');
    let obj;
    try{obj=JSON.parse(raw);}catch{this.fail('INVALID_SSE_JSON');return;}
    if(obj===null||typeof obj!=='object'||Array.isArray(obj)){
      this.fail('INVALID_SSE_SHAPE');return;
    }
    if(obj.error){this.fail('UPSTREAM_ERROR_EVENT');return;}
    if(!Array.isArray(obj.choices)) {this.fail('MISSING_CHOICES');return;}
    if(obj.choices.length>1){this.fail('MULTIPLE_CHOICES_UNSUPPORTED');return;}
    if(!obj.choices.length)return; // Usage metadata.
    const c=obj.choices[0];
    if(c?.index!==undefined && c.index!==0)this.fail('NONZERO_CHOICE_INDEX');
    if(c?.message)this.fail('NON_STREAM_MESSAGE');
    const d=c?.delta;
    if(d && typeof d==='object'){
      if(typeof d.content==='string' && d.content.length){
        this.contentFrames++;
        this.contentChars+=d.content.length;
        if(this.firstContentMs===null)this.firstContentMs=Math.round(performance.now()-this.started);
        this.hash.update(d.content,'utf8');
      } else if(d.content!==undefined&&d.content!==null&&typeof d.content!=='string'){
        this.fail('NON_TEXT_CONTENT');
      }
      if((Array.isArray(d.tool_calls)&&d.tool_calls.length)||d.function_call)this.toolFrames++;
    }
    if(c?.finish_reason!==undefined&&c.finish_reason!==null) {
      this.finishes.push(String(c.finish_reason));
      if(this.finishes.length>1)this.fail('DUPLICATE_FINISH');
    }
  }
  finish(){
    if(this.ended)throw Error('AUDIT_ALREADY_FINISHED');
    this.ended=true;
    try{this.addText(this.decoder.decode());}
    catch{this.fail('MALFORMED_UTF8');}
    if(this.partial.length||this.data.length)this.fail('INCOMPLETE_SSE_EOF');
    if(this.doneCount!==1)this.fail('MISSING_EXACTLY_ONE_DONE');
    if(this.finishes.length!==1)this.fail('MISSING_EXACTLY_ONE_FINISH');
    if(this.finishes.length===1 && this.finishes[0]!=='stop')this.fail('NONSTOP_FINISH_REASON');
    if(this.toolFrames)this.fail('UNEXPECTED_TOOL_CALL');
    if(this.contentFrames===0)this.fail('NO_ASSISTANT_CONTENT');
    return {
      verdict:this.errors.length?'RECEIPT_INCOMPLETE_OR_UNVERIFIED':'SSE_RECEIVED_ONE_COMPLETE_TEXT_TURN',
      scope:'single public HTTP client receipt; not DSH UI receipt or Relay durable ACK',
      events:this.events,
      text_delta_frames:this.contentFrames,
      text_characters:this.contentChars,
      tool_delta_frames:this.toolFrames,
      first_text_delta_ms:this.firstContentMs,
      finish_reason:this.finishes[0]??null,
      done_count:this.doneCount,
      text_sha256:this.hash.digest('hex'),
      anomalies:this.errors,
      issued_post:false,
      response_body_logged:false,
      credentials_logged:false
    };
  }
}
