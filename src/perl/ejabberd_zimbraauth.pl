#!/usr/bin/perl -w

use FindBin;
use lib "$FindBin::Bin/";
use Cwd;
use LWP::UserAgent;
use File::Basename;
use XmlElement;
use XmlDoc;
use Soap;
use lib '.';
use Log::Log4perl qw(:easy);

#configure the following 5 variables to match your ZCS installation
my $ZCS_ADMIN = "admin"; #login for zimbra admin account with sufficient rights to call GetAccountInfoRequest on any user.
my $ZCS_ADMIN_PW = "test123"; #password for zimbra admin account set in $ZCS_ADMIN.
my $ZCS_HOST_PORT="localhost"; #host:port where Zimbra SOAP interface is running. Usually port is not needed.
my $ZCS_ADMIN_HOST_PORT="localhost:7071"; #host:port where Zimbra Admin SOAP interface is running.
my $ZCS_PROTO="http"; #http or https protocol that this script will use to access Zimbra SOAP interface

my $ACCTNS = "urn:zimbraAccount";
my $ADMINNS = "urn:zimbraAdmin";
my $SOAP = $Soap::Soap12;
my $url = $ZCS_PROTO."://".$ZCS_HOST_PORT."/service/soap/";
my $adminURL = "https://".$ZCS_ADMIN_HOST_PORT."/service/admin/soap/";
my $adminToken;
my $logfile = "zimbraauth.log";

#try putting our log file with other log files under ejabberd and mongooseIM
if (defined $ENV{"EJABBERD_LOG_PATH"}) {
    my($fname, $dir, $suffix) = fileparse($ENV{"EJABBERD_LOG_PATH"});
    $logfile = $dir.$logfile;
} elsif (defined $ENV{"PROGNAME"} && lc($ENV{"PROGNAME"}) =~ /mongooseim/) {
    $logfile = "log/".$logfile;
} elsif (lc(getcwd()) =~ /mongooseim/) {
    $logfile = "log/".$logfile;
}
Log::Log4perl->easy_init({level => $INFO, file => ">> $logfile", });

sub LOG() {
    my ($str) = @_;
    INFO($$." ".$str);
}

sub get_token() {
    my ($username, $host, $password) = @_;
    my $d = new XmlDoc;
    my $name = &concat_username($username, $host);
    $d->start('AuthRequest', $ACCTNS);
    $d->add('account', undef, { by => "name"}, $name);
    $d->add('password', undef, undef, $password);
    $d->end();
    my $authResponse = $SOAP->invoke($url, $d->root());
    my $tNode = $authResponse && $authResponse->find_child('authToken');
    return ($tNode && $tNode->content) || 0;
}

sub get_admin_token() {
    my $d = new XmlDoc;
    $d->start('AuthRequest', $ADMINNS);
    $d->add('account', undef, { by => "name"}, $ZCS_ADMIN);
    $d->add('password', undef, undef, $ZCS_ADMIN_PW);
    $d->end();
    my $authResponse = $SOAP->invoke($adminURL, $d->root());
    my $tNode = $authResponse && $authResponse->find_child('authToken');
    return ($tNode && $tNode->content) || 0;
}

sub concat_username() {
    my ($username, $host) = @_;
    return ($username =~ /@/) ? $username : ($username . "@" . $host);
}

sub auth_password() {
    my ($username, $host, $password) = @_;
    my $token = &get_token($username, $host, $password);
    return ($token)? 1:0;
}

sub auth_token() {
    my ($username, $host, $token) = @_;
    my $name = &concat_username($username, $host);
    my $context = $SOAP->zimbraContext($token);
    my $d = new XmlDoc;
    $d->start('GetInfoRequest', $ACCTNS, {sections=>'mbox'});
    $d->end();
    &LOG("sending GetInfoRequest to ".$url);
    my $response = $SOAP->invoke($url, $d->root(), $context);
    my $nNode = $response && $response->find_child('name');
    my $tName = $nNode && $nNode->content;
    return ($tName && ($tName eq $name));
}

sub is_user() {
    my ($username, $host, $renewToken) = @_;
    if (!$adminToken || $renewToken) {
        $adminToken = &get_admin_token();
    }
    my $name = &concat_username($username, $host);
    my $context = $SOAP->zimbraContext($adminToken);
    my $d = new XmlDoc;
    $d->start('GetAccountInfoRequest', $ADMINNS);
    $d->start('account', undef, {by=>'name'}, $name);
    $d->end();
    $d->end();
    my $response = $SOAP->invoke($adminURL, $d->root(), $context);
    if ($response->name eq 'Fault' && !$renewToken) {
        my $code = $response->find_descendant('Detail','Error','Code');
        if ($code && $code->content eq 'service.AUTH_REQUIRED') { # Token is invalid, get a new one and try again
            return &is_user($username, $host, 1);
        }
    }
    return $response->name eq 'GetAccountInfoResponse';
}

sub read_input() {
    while(!sysread(STDIN, $b, 2)) { # run until input
        sleep(1);
    }
    $length = unpack("n", $b); # Decode as unsigned short (big endian)
    #&LOG("Reading length field from ejabberd; input length is ".($length||"ZERO")." bytes");
    if ($length) {
        sysread(STDIN, $buffer, $length);
        $buffer =~ s/^\s+//; # trim the string
        $buffer =~ s/\s+$//;
        #&LOG("Read ".$length." bytes of data from ejabberd: ".$buffer);
        return split(/:/, $buffer);
    }
}

sub write_output() {
    my ($answer) = @_;
    syswrite(STDOUT, pack("nn", 2, $answer ? 1 : 0)); # Encode as unsigned short (big endian)
}

$SIG{TERM} = sub {
    &LOG("SHUTTING DOWN (SIGTERM) ...");
    exit(0);
};

$SIG{INT} = sub {
    &LOG("SHUTTING DOWN (SIGINT) ...");
    exit(0);
};

&LOG("STARTING UP... working path is ".getcwd());
while (1) {
    my ($op, $username, $host, $password) = read_input();
    #&LOG("{op: ".($op||"NULL").", username: ".($username||"NULL").", host: ".($host||"NULL").", password: ".($password||"NULL")."}");
    $answer = 0;

    if ($op eq "auth") {
        if(length($password)<100) {
            $answer = &auth_password($username, $host, $password);
        } else {
            my $pwdPrefx = substr ($password,0,10);
            if($pwdPrefx eq "__zmauth__") {
                $answer = &auth_token($username, $host, substr ($password,10));
            } else {
               &LOG("unknown auth token prefix")
            }
        }
    }

    if ($op eq "isuser") {
       $answer = &is_user($username, $host, false);
    }
    &LOG("{op: ".($op||"NULL").", username: ".($username||"NULL").", host: ".($host||"NULL").($password?", password: <HIDDEN>":"")."} => ".($answer?"OK":"FAIL"));
    &write_output($answer);
}