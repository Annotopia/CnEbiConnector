class UrlMappings {

	static mappings = {
		
		"/cn/ebi/textmine"{
			controller = "ebi"
			action = [POST:"textmine"]
		}
		
		"/$controller/$action?/$id?"{
			constraints {
				// apply constraints here
			}
		}

		"/"(view:"/index")
		"500"(view:'/error')
	}
}
