def runStages() {
	try {
	stage('pre-build'){
		def mplabxPath= sh (script: 'update-alternatives --list MPLABX_PATH',returnStdout: true).trim()
		def compilerPath= sh (script: 'update-alternatives --list XC8_PATH',returnStdout: true).trim()										
		def pDir = "${mplabxPath}/packs"				
		def ver = compilerPath.split('/')[4].substring(1)		
		
		execute("git clone https://bitbucket.microchip.com/scm/citd/tool-mplabx-c-project-generator.git")					
		execute("cd tool-mplabx-c-project-generator && node configGenerator.js sp=../ v8=${ver} packs=${pDir}")	
	}
	stage('build') {
		execute("git clone https://bitbucket.microchip.com/scm/citd/tool-mplabx-c-build.git")								
		execute("cd tool-mplabx-c-build && node buildLauncher.js sp=../ rp=./output genMK=true")
	}
	stage('github-deploy') {
		if(env.BRANCH_NAME == 'test') {
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
		if(env.BRANCH_NAME == 'develop'){
			echo "Portal deploy"
		}
	}
	} finally {
		// Archive the build output artifacts.
		archiveArtifacts artifacts: "tool-mplabx-c-build/output/**", allowEmptyArchive: true, fingerprint: true
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
return this