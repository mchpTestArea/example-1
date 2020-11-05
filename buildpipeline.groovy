def runStages() {
	stage('pre-build'){
		def mplabxPath= sh (script: 'update-alternatives --list MPLABX_PATH',returnStdout: true).trim()
		def compilerPath= sh (script: 'update-alternatives --list XC8_PATH',returnStdout: true).trim()										
		def pDir = "${mplabxPath}/packs"				
		def ver = compilerPath.split('/')[4].substring(1)		
		
		execute("git clone https://bitbucket.microchip.com/scm/citd/tool-mplabx-c-project-generator.git")					
		execute("cd tool-mplabx-c-project-generator2 && node configGenerator.js sp=../ v8=${ver} packs=${pDir}")	
	}
	stage('build') {
		execute("git clone https://bitbucket.microchip.com/scm/citd/tool-mplabx-c-build.git")								
		execute("cd tool-mplabx-c-build && node buildLauncher.js sp=../ rp=./output genMK=true")
	}
}
def execute(String cmd) {
	if(isUnix()) {
		sh cmd
	} else {
		bat cmd
	}
}
return this