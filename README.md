# Groovy Rest

A simple Groovy script wrapping the Jersey REST client library.

Provides an easy way of using RESTful APIs with Groovy, using either static method calls or objects.

# Dependencies

Uses Grape to grab the Jersey-client and Jersey-core libraries.

# Usage

Drop Rest.groovy next to your groovy script, and use it like so:

# Basic Usage

Statically import the HTTP primitive operations (GET,POST,PUT,DELETE), or use them directly:

    import static Rest.GET
    import static Rest.POST

    def result = GET 'http://example.com/api/sometthing/resource'
    def result2 = POST 'http://example.com/api/sometthing/resource', 'Text content'

You can pass Headers and query parameters as arguments to the operations. 
    
    result = GET 'http://example.com/api/sometthing/resource', ['X-My-Header':'abc'], [q:'searchterm']

PUT and POST also accept either string content, or a closure that will build XML.  If using a closure, specify it last.

    result2 = POST 'http://example.com/api/sometthing/resource', 'Text content', ['X-My-Header':'abc'], [q:'searchterm']

    //use a closure to build xml

    result2 = POST('http://example.com/api/sometthing/resource', ['X-My-Header':'abc'], [q:'searchterm']){
        mycontent{
            myelement(someattribute: 'hello'){
                this('contains text')
            }
        }
    }

# Object Usage

You can treat RESTful endpoints more like objects by instantiating the Rest class, and the "get","post","put", "delete" methods:

    def rest = 'http://example.com/api/base' as Rest
    def response= rest.post("content",[header:'value'],[query:'value'])
    response= rest.get([header:'value'],[query:'value'])

Create a new Rest object by appending a subpath of the original, or the overridden "+" plus operator:

    def sub = rest + '/sub/path'
    sub = rest.path('/sub/path')

Overridden operators allow some groovy stuff.

GET a resource using the "getAt" operator ("[]"):

    def dom = rest["/sub"].XML 

POST XML using left shift with a closure:

    response = rest << {
        xml{
            content('text')
        }
    }

PUT XML or other content by using "putAt" ("[]=") operator with a closure or a string:

     response = rest['/sub/path']={
         element('text')
     }
     response = rest['/sub/path']="content"

# responses

The result of all requests is the Jersey [ClientResponse](http://jersey.java.net/nonav/apidocs/latest/jersey/com/sun/jersey/api/client/ClientResponse.html) object.

Some sugar for ClientResponse has been added:

    def dom = result.XML //parse XML response with Groovy XmlParser.
    def text = result.text //return content as a String
    result.hasContentType 'text/xml' //returns true/false
    result.hasCompatibleType '*/xml' //returns true/false, will match wildcards
    result.requireContentType 'text/xml' //throws an exception, or calls a handler if the content type doesn't match, otherwise returns the ClientResponse
    result.requireStatus 201 //throws an exception, or calls a handler if the result status doesn't match, otherwise returns the ClientResponse

We can use this to chain our request build:

    def dom = GET('http://example.com/api/sometthing/resource.xml')
                .requireContentType('text/xml')
                .requireStatus(201)
                .XML

# Defaults for requests

Set static defaults to apply to all method calls.

    Rest.defaultAccept='application/*+xml'
    Rest.defaultHeaders=Rest.basicAuthHeader(user,pass)
    Rest.baseUrl="${protocol}://${host}:${port}/api"
    Rest.xmlDeclaration=true //include <?xml..> declaration in generated XML requests

Setting the static `baseUrl` value means you can operate on sub-paths of this URL easily with static methods or objects:

    def dom = GET('/sub/path').XML
    def sub = '/sub/path' as Rest

# Error handling

You can define default handlers for certain failure types, such as generic failure (HTTP status), or unexpected content type.

By default, any HTTP status outside of the 20x range is considered a failure.

Here is our API handler that can parse the API error response:

    def apiErrorHandler(response){
        def err = response.XML
        die "API Error: ${err.'@apiErrorCode'}: ${err.'@errorMessage'}"
    }

Assign generic failure handler for HTTP status codes that are not succesful:

    Rest.failureHandler={response->
        if(response.hasContentType('application/my.api.error+xml')){
            apiErrorHandler(response)
        }else{
            die("Request failed: ${response}")
        }
    }

Assign a failure handler for unexpected content type:

    Rest.contentTypeFailureHandler={type,response->
        if(response.hasContentType('application/my.api.error+xml')){
            apiErrorHandler(response)
        }else{
            die("Expected ${type}, but response was ${response.type}: ${response}")
        }
    }

Now `requireContentType` and `requireStatus` will call these handlers if they fail.

# Debugging

All requests/responses be output on the console by calling `Rest.debug(PrintStream)`:

    if(isDebug){
        Rest.debug(System.out)
    }

# TODO

Some enhancements that could be made:

* support JSON requests in builder, and responses via a "getJSON" method on ClientResponse
* support more features of Jersey client, like custom response types
