#!/usr/bin/env groovy
@Grab(module="jersey-client", group="com.sun.jersey",version="1.12")
@Grab(module="jersey-core", group="com.sun.jersey",version="1.12")
import com.sun.jersey.api.client.*
import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.MediaType
import com.sun.jersey.api.representation.Form
import com.sun.jersey.api.client.filter.LoggingFilter
import groovy.xml.MarkupBuilder

/**
 * Simplified groovy REST utility using Jersey client library.
 * <p>
 * You can configure static defaults to use the static methods, or use use an instance of this class.
 * </p>
 * <p>Static configuration:</p>
 * <pre>
 * //set Accept header default value
 * Rest.defaultAccept='application/*+xml; version=1.5'
 * //set other default request headers
 * Rest.defaultHeaders=['Authorization':'Basic '+"${user}:${pass}".toString().bytes.encodeBase64().toString()]
 * //set base URL for requests
 * Rest.baseUrl="http://host/path"
 * //set handler for non 200 responses
 * Rest.failureHandler={response->
 *   die("Request failed: ${response}")
 * }
 * //set handler for unexpected content types, used with response.requireContentType(type)
 * Rest.contentTypeFailureHandler={response->
 *   die("ContentType not expected: ${response.type}: ${response}")
 * }
 * </pre>
 * <p>Invocation can be performed statically using the GET and POST methods:</p>
 * <pre>
 * import static Rest.GET
 * import static Rest.POST
 * def response= GET "http://host/path"
 * //or set default base path
 * Rest.baseUrl="http://host/path"
 * response = POST "/subpath","content"
 * </pre>
 * <p>Non-static usage is performed by creating an instance with an absolute or relative URL</p>
 * <pre>
 * def rest = new Rest("http://host/path")
 * def response= rest.post("content",[header:'value'],[query:'value'])
 * response= rest.get([header:'value'],[query:'value'])
 * // use a sub path on the original
 * def resp2 = rest.path("/sub").get()
 * // require content type. If it is not correct, the contentTypeFailureHandler will be called if set
 * resp2.requireContentType("application/xml")
 * </pre>
 * <p>Shortcuts:</p>
 * <pre>
 * //convert String to Rest object:
 * def rest = "http://host/path" as Rest
 * 
 * //add path to existing Rest object:
 * def rest2 = rest + '/subpath'
 *
 * //POST xml content using leftshift and groovy markupbuilder
 * def resp = rest2 &lt;&lt; {
 * 	 content(attr:'value'){
 *      sub('text')
 *	 }	
 * }
 *
 * //GET subpath
 * def resp2 = rest['/subpath']
 *
 * //PUT xml at subpath
 * def resp3 = rest['/subpath']={
 *     element('text')
 * }
 *
 * //convert XML response into groovy nodes via XmlParser using .XML
 * def xml = rest['/path'].XML
 *
 * //get textual response using the 'text' property of the response
 * def text = rest['/path'].text
 * </pre>
 * @author Greg Schueler <a href="mailto:greg@dtosolutions.com">greg@dtosolutions.com</a>
 */
public class Rest{

	static{
		ClientResponse.metaClass.getXML={
			new XmlParser(false,true).parse(delegate.getEntity(InputStream.class))
		}
		ClientResponse.metaClass.getText={
			delegate.getEntity(String.class)
		}
		ClientResponse.metaClass.requireContentType={type->
			if(!delegate.hasContentType(type)){
				if(Rest.contentTypeFailureHandler) Rest.contentTypeFailureHandler.call(type,delegate)
				else throw new RuntimeException("Expected ${type}, but response was ${delegate.type}: ${delegate}")
			}
			delegate
		}
		ClientResponse.metaClass.requireCompatibleType={type->
			if(!delegate.hasCompatibleType(type)){
				if(Rest.contentTypeFailureHandler) Rest.contentTypeFailureHandler.call(type,delegate)
				else throw new RuntimeException("Expected ${type}, but response was ${delegate.type}: ${delegate}")
			}
			delegate
		}
		ClientResponse.metaClass.hasContentType={type->
			delegate.type==type as MediaType
		}
		ClientResponse.metaClass.hasCompatibleType={type->
			delegate.type.isCompatible(type as MediaType)
		}
		ClientResponse.metaClass.requireStatus={status->
			if(delegate.status!=status){
				if(Rest.failureHandler) Rest.failureHandler.call(delegate)
				else throw new RuntimeException("Expected ${status}, but response was ${delegate.status}: ${delegate}")
			}
		}
		String.metaClass.asType = { Class c -> 
			if (c==MediaType) MediaType.valueOf(delegate)
			else if (c==Rest) new Rest(delegate)
			else delegate.asType(c) 
		}
		WebResource.Builder.metaClass.leftShift<<{Map map->
			map?.each{
				delegate.header(it.key,it.value)
			}
		}
	}

	static Client client = Client.create()
	/**
	 * Default accept header MediaType
	 */
	def static defaultAccept=MediaType.APPLICATION_XML_TYPE
	/**
	 * Map of default request headers
	 */
	def static defaultHeaders=[:]
	/**
	 * base URL for all requests
	 */
	def static baseUrl
	def static mock
	/**
	 * Closure that will be called if the response is not a successful status value. Argument is a ClientResponse object.
	 */
	def static failureHandler
	/**
	 * Closure that will be called if ClientResponse.requireContentType doesn't match the response. Arguments are (content type, response)
	 */
	def static contentTypeFailureHandler

	/**
	 * if true, include xml declaration in any generated xml
	 */
	def static xmlDeclaration=true

	/**
	 * Jersey client Resource
	 */
	def resource
	/**
	 * Request headers to send for any requests for this resource
	 */
	def headers=[:]
	/**
	 * Accept header value
	 */
	def accept=defaultAccept
	
	def static addFilter(filter){
		client.addFilter(filter)
	}
	/**
	 * Print debug output for all requests/responses to the given PrintStream
	 */
	def static debug(PrintStream out){
		addFilter(new LoggingFilter(out))
	}
	/**
	 * Create a new Rest given the URL path. If the path is an absolute URL, 
	 * it will be used.  If it is a relative URL and the static baseUrl has been set, it will
	 * be added relative to the baseUrl.
	 */
	public Rest(String path){
		if(baseUrl && !path.startsWith('http')){
			resource=client.resource(baseUrl).path(path)
		}else{
			resource=client.resource(path)
		}
	}
	private Rest(WebResource resource){
		this.resource=resource
	}
	/**
	 * Override the '+' operator to support appending a URL path to a Rest instance.
	 */
	public Rest plus(String path){
		new Rest(resource.path(path))
	}
	/**
	 * Override the getAt operator to support appending a URL path to a Rest instance and executing a GET request immediately.
	 */
	public getAt(String path){
		new Rest(resource.path(path)).get()
	}
	/**
	 * Override the putAt operator to support appending a URL path to a Rest instance and executing a POST request immediately.
	 */
	public putAt(path,content){
		new Rest(resource.path(path)).post(content)
	}
	/**
	 * Override the leftShift '&lt;&lt;' operator to support POST using XML content defined in a builder closure.
	 */
	public leftShift(Closure obj){
		post(xmlContent(obj))
	}
	private makeRequest(builder,Closure clos){
		def response=builder.with(mock?:clos)
		
		if(failureHandler && (response.status <200 || response.status>=300)){
			failureHandler(response)
		}
		response
	}

	private query(params=[:]){
		if(params){
			MultivaluedMap<String, String> qparams = new MultivaluedMapImpl();
			qparams.putAll(params)
			return resource.queryParams(qparams)
		}
		resource
	}
	private addHeaders(builder,headers){
		builder<<defaultHeaders
		builder<<this.headers
		builder<<headers
	}

	/**
	 * POST request to the given relative or absolute URL.
	 * @param url relative to the baseUrl, or an absolute URL
	 * @param content any text content, or a closure for building XML content
	 * @param headers request header map
	 * @param params request params map
	 */
	public static POST(url, content='', headers=[:],params=[:]){
		new Rest(url).post(content,headers,params)
	}

	/**
	 * GET request to the given relative or absolute URL.
	 * @param url relative to the baseUrl, or an absolute URL
	 * @param headers request header map
	 * @param params request params map
	 */
	public static GET(url, headers=[:],params=[:]){
		new Rest(url).get(headers,params)
	}
	/**
	 * PUT request to the given relative or absolute URL.
	 * @param url relative to the baseUrl, or an absolute URL
	 * @param content any text content, or a closure for building XML content
	 * @param headers request header map
	 * @param params request params map
	 */
	public static PUT(url, content='',headers=[:],params=[:]){
		new Rest(url).put(content,headers,params)
	}
	/**
	 * DELETE request to the given relative or absolute URL.
	 * @param url relative to the baseUrl, or an absolute URL
	 * @param headers request header map
	 * @param params request params map
	 */
	public static DELETE(url, headers=[:],params=[:]){
		new Rest(url).delete(headers,params)
	}
	/**
	 * Return a map containing request header for HTTP Basic authentication
	 * @param user the username string
	 * @param pass the password string
	 */
	public static basicAuthHeader(user,pass){
		['Authorization':'Basic '+"${user}:${pass}".toString().bytes.encodeBase64().toString()]
	}
	private static makeContent(content){
		if(content instanceof Closure){
			xmlContent(content)
		}else{
			content
		}
	}
	private static xmlContent(Closure clos){
		def writer = new StringWriter()
		def xml = new MarkupBuilder(writer)
		if(xmlDeclaration){
			xml.mkp.xmlDeclaration(version:'1.0',encoding:'UTF-8')
		}
		clos.delegate=xml
		clos.call()
		writer.toString()
	}

	private build(headers,params){
		def builder= query(params).accept(accept)
		addHeaders(builder,headers)
		builder
	}

	/**
	 * GET request for this URL.
	 * @param headers request header map
	 * @param params request params map
	 */
	public get(headers=[:],params=[:]){
		makeRequest(build(headers,params)){
        	get(ClientResponse.class);
        }
	}
	/**
	 * POST request to this URL.
	 * @param content any text content, or a closure for building XML content
	 * @param headers request header map
	 * @param params request params map
	 */
	public post(content,headers=[:],params=[:]){
		def value=makeContent(content)
		makeRequest(build(headers,params)){
			post(ClientResponse.class,value);
		}
	}
	/**
	 * POST request to this URL, using a builder closure to define XML content
	 * @param headers request header map
	 * @param params request params map
	 * @param clos a closure for building XML content
	 */
	public post(headers,params,Closure clos){
		def content=makeContent(clos)
		makeRequest(build(headers,params)){
			post(ClientResponse.class,content);
		}
	}
	/**
	 * POST request to this URL, using a builder closure to define XML content
	 * @param headers request header map
	 * @param clos a closure for building XML content
	 */
	public post(headers,Closure clos){
		post(headers,[:],clos)
	}
	/**
	 * POST request to this URL, using a builder closure to define XML content
	 * @param clos a closure for building XML content
	 */
	public post(Closure clos){
		post([:],[:],clos)
	}
	/**
	 * PUT request to this URL.
	 * @param content any text content, or a closure for building XML content
	 * @param headers request header map
	 * @param params request params map
	 */
	public put(content,headers=[:],params=[:]){
		def value=makeContent(content)
		makeRequest(build(headers,params)){
			put(ClientResponse.class,value);
		}
	}
	/**
	 * DELETE request for this URL.
	 * @param headers request header map
	 * @param params request params map
	 */
	public delete(headers=[:],params=[:]){
		makeRequest(build(headers,params)){
        	delete(ClientResponse.class);
        }
	}
	def String toString(){
		resource.toString()
	}
}
