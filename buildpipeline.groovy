def runStages() {
	try {
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
			echo "Portal deploy"
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
    mail to: "${params.NOTIFICATION_EMAIL}",
    subject: "Failed Pipeline: ${currentBuild.fullDisplayName}",
    body: "Pipeline failure. ${env.BUILD_URL}"
}

return this