def runStages() {
	try {	
		stage('metadata') {
			def jsonObj = readJSON file:".main-meta/main.json"		
			def version = "${jsonObj.content.version}".trim()
			
			if(version != "" || env.TAG_NAME ) {				
				download("metadata-schema","1.1.0")
				download("tool-metadata-validator","1.0.0")										
				execute("cd tool-metadata-validator && python metadata-validator.py -data '../.main-meta/main.json' -schema '../metadata-schema/main-schema.json'")
								
				def githubObj = getGiHubInfo()
			
				if(githubObj.repoName != jsonObj.content.projectName) {
					execute("echo 'Project name in metadata file does not match with GitHub repository name.' && exit 1")						
				}
				
				if(env.TAG_NAME =~ env.SEMVER_REGEX) {
					if(env.TAG_NAME != jsonObj.content.version) {
						execute("echo 'Version in metadata file does not match with TAG_NAME.' && exit 1") 
					}
				}
			}		
		}
	
		stage('pre-build'){
			def mplabxPath= sh (script: 'update-alternatives --list MPLABX_PATH',returnStdout: true).trim()
			def compilerPath= sh (script: 'update-alternatives --list XC8_PATH',returnStdout: true).trim()										
			def pDir = "${mplabxPath}/packs"				
			def ver = compilerPath.split('/')[4].substring(1)		
			
			download("tool-mplabx-c-project-generator","1.0.0")					
			execute("cd tool-mplabx-c-project-generator && node configGenerator.js sp=../ v8=${ver} packs=${pDir}")	
		}
		
		stage('build') {
			download("tool-mplabx-c-build","1.0.0")								
			execute("cd tool-mplabx-c-build && node buildLauncher.js sp=../ rp=./output genMK=true")
		}
		
		stage('github-deploy') {
			if(env.TAG_NAME =~ env.SEMVER_REGEX) {
				def githubObj = getGiHubInfo()					
				download("tool-github-deploy","1.0.0")		
				execute("chmod +x ./tool-github-deploy/tool-github-deploy/tool-github-deploy.py")  
						
				withCredentials([usernamePassword(credentialsId: 'BD1085_GitHub_Token', usernameVariable: 'USER_NAME', passwordVariable:'USER_PASS' )]) {					
					execute("python ./tool-github-deploy/tool-github-deploy/tool-github-deploy.py -deploy=true -gpat=${USER_PASS} -dgid=${USER_NAME} -dburl=${env.BITBUCKET_URL} -dgurl=${env.GITHUB_URL} -dtag=${env.TAG_NAME} -dmfd=true")						
					execute("python ./tool-github-deploy/tool-github-deploy/tool-github-deploy.py -rlo=true -gpat=${USER_PASS}  -rpn=${githubObj.repoName} -rltv=${env.TAG_NAME} -rltt=${env.TAG_NAME} -dmfd=true")	
				}
			}		
		}
		
		stage('portal-deploy') {
			if(env.TAG_NAME =~ env.SEMVER_REGEX){
				def metadata = readJSON file:".main-meta/main.json"					
				def version = metadata.content.version
				def project = metadata.content.projectName

				if(version == env.TAG_NAME) {				
					def cmdArgs = "'{\"repoOwnerName\":\"$env.GITHUB_OWNER\",\"repoName\":\"$project\",\"tagName\":\"$version\"}'"
					cmdArgs = cmdArgs.replaceAll("\"","\\\\\"")						
				
					execute("git clone https://bitbucket.microchip.com/scm/portal/bundles.git")
					execute("cd bundles && chmod 755 ./portal-client-cli-linux")						
					download("tool-portal-client-launcher","1.0.0")
					execute("cd tool-portal-client-launcher && node portalLauncher.js -app=../bundles/portal-client-cli-linux -cmd=\"uploadGitHub ${cmdArgs}\"")
					sendSuccessfulPortalDeploymentEmail()
				} else {
					execute("echo 'Tag name is not equal to metadata content version.' && exit 1")
				}
			}
		}	
	} finally {
		// Archive the build output artifacts.
		archiveArtifacts artifacts: "tool-mplabx-c-build/output/**", allowEmptyArchive: true, fingerprint: true
		
		// send an email
		if(currentBuild.result != 'SUCCESS') {
			echo "sending notification mail ${params.NOTIFICATION_EMAIL} ${currentBuild.fullDisplayName} ${env.BUILD_URL}"
			sendPipelineFailureEmail()
		}
	}
}
def execute(String cmd) {
	if(isUnix()) {
		sh cmd
	} else {
		bat cmd
	}
}
def download(String toolName,String toolVersion) {
	def repo = "ivy/citd"
	def url = "${env.ARTIFACTORY_SERVER}/${repo}/${toolName}/${toolVersion}/${toolName}-${toolVersion}.zip"
	def response =sh(script:"curl ${url} -o ${toolName}.zip",returnStdout: true).trim()
	unzip dir:"${toolName}", quiet: true, zipFile: "${toolName}.zip"	
	execute("rm -rf ${toolName}.zip")
}
def getGiHubInfo() {
	def githubObj = [
		'ownerName':'',
		'repoName':''
		]
	String[] splitURLString = "${env.GITHUB_URL}".split("/")
	githubObj.repoName = splitURLString[splitURLString.size()-1]
	githubObj.repoName = githubObj.repoName.replace(".git","")
	githubObj.ownerName = splitURLString[splitURLString.size()-2]
	return githubObj
}

def sendPipelineFailureEmail () {			  
    emailext( to: "${params.NOTIFICATION_EMAIL}",
    subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
    body: "Pipeline failure. ${env.BUILD_URL}")
}

return this